from __future__ import annotations

import io
import json
import random
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from PIL import Image

from modcompiler.auto_create_modrinth import (
    GenerateInputBundle,
    GenerateOptions,
    build_ai_user_message,
    build_source_clarification_context_text,
    build_source_clarification_user_message,
    build_generated_repo_readme,
    choose_best_summary_candidate,
    choose_preferred_chat_text,
    build_summary_correction_user_message,
    build_summary_specific_guidance,
    build_summary_user_message,
    build_aihorde_image_submit_params,
    build_modrinth_category_guidance,
    build_modrinth_project_url,
    build_modrinth_version_url,
    compose_description_poster_image,
    choose_available_modrinth_project_identity,
    compute_description_layout_variables,
    compute_logo_layout_variables,
    copy_visual_background_asset,
    build_effective_source_verified_text,
    discover_generate_input_bundles,
    discover_background_images,
    build_visual_html_document,
    build_visual_prompt_user_message,
    build_projectinfo_text,
    build_project_create_payload,
    build_version_create_payload,
    build_project_draft,
    build_version_draft,
    decode_base64_image_payload,
    default_categories_for_metadata,
    create_modrinth_version_after_project_ready,
    filter_generate_input_bundles,
    detect_image_extension_from_bytes,
    extract_json_object,
    extract_best_answer_candidate_text,
    extract_html_document_fragments,
    extract_aihorde_generated_images,
    extract_aihorde_job_id,
    expand_supported_versions,
    extract_answer_from_reasoning_text,
    finalize_category_selection,
    finalize_generated_listing,
    generate_bundle_for_input_bundle,
    generate_bundle_visual_assets,
    inject_description_image_into_body,
    inject_description_images_into_body,
    is_transient_github_cli_error,
    is_bundle_approved_for_draft,
    is_bundle_verified,
    load_template_examples,
    normalize_short_description,
    normalize_background_choice,
    normalize_visual_design_response,
    normalize_visual_prompt_text,
    normalize_publish_via,
    parse_ai_listing_response,
    parse_ai_summary_response,
    parse_github_repo_from_remote,
    parse_visual_prompt_response,
    prepare_generated_logo_icon,
    promote_published_bundle_visibility,
    publish_bundle,
    repair_summary_candidate,
    simplify_summary_candidate,
    request_local_c05_chat,
    run_github_cli,
    score_summary_candidate,
    render_title_overlay_on_image,
    recommend_background_images,
    render_html_document_to_image,
    remote_publish_runtime_paths,
    resolve_modrinth_project_identity,
    wait_for_modrinth_project_ready,
    resolve_description_image_files_for_upload,
    resolve_decompiled_source_roots,
    sanitize_sensitive_subprocess_text,
    save_vertical_poster_slices,
    set_verify_file_verified,
    summary_text_looks_glitched,
    source_path_label,
    strip_project_external_links,
    strip_generated_description_images,
    strip_empty_markdown_heading_blocks,
    sync_bundle_github_links,
    sync_project_icon,
    summarize_result_counts,
    text_looks_clipped_or_partial,
    sync_project_description_image,
)
from modcompiler.common import ModCompilerError, load_json, write_json


REPO_ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = REPO_ROOT / "version-manifest.json"


class AutoCreateModrinthTests(unittest.TestCase):
    def test_parse_ai_listing_response_accepts_fenced_json(self) -> None:
        raw = """
        Here you go:

        ```json
        {
          "name": "Allow Disconnect",
          "long_description": "A longer markdown body."
        }
        ```
        """
        parsed = parse_ai_listing_response(raw)
        self.assertEqual(parsed["name"], "Allow Disconnect")
        self.assertIn("longer markdown", parsed["long_description"])

    def test_parse_ai_listing_response_accepts_field_block_without_json_braces(self) -> None:
        parsed = parse_ai_listing_response(
            """
            name: TNT Duper
            long_description: # TNT Duper

            Dispensers fire TNT without consuming the TNT item.
            categories: ["technology"]
            additional_categories: ["utility"]
            client_side: optional
            server_side: required
            license_id: (empty string)
            license_url:
            """
        )
        self.assertEqual(parsed["name"], "TNT Duper")
        self.assertIn("Dispensers fire TNT", parsed["long_description"])
        self.assertEqual(parsed["categories"], ["technology"])

    def test_parse_ai_summary_response_accepts_plain_text(self) -> None:
        parsed = parse_ai_summary_response(
            "Hate when mobs survive daylight? This mod makes every hostile mob burn in the sun."
        )
        self.assertIn("burn", parsed.lower())

    def test_text_looks_clipped_or_partial_flags_lowercase_suffix(self) -> None:
        self.assertTrue(text_looks_clipped_or_partial("don't burn in daylight? This mod makes it so that ALL mobs burn in daylight."))
        self.assertFalse(text_looks_clipped_or_partial("Hate that mobs don't burn in daylight? This mod makes it so that ALL mobs burn in daylight."))

    def test_choose_preferred_chat_text_prefers_full_reasoning_answer_when_stream_is_clipped(self) -> None:
        stream = "lurking in daylight? This mod makes it so that all hostile mobs burn in the sun."
        reasoning = "Hate that mobs keep lurking in daylight? This mod makes it so that all hostile mobs burn in the sun."
        self.assertEqual(choose_preferred_chat_text(stream, reasoning), reasoning)

    def test_repair_summary_candidate_rewrites_vague_daylight_burn_summary(self) -> None:
        repaired = repair_summary_candidate(
            "Hate that mobs just wander around in daylight unscathed? Hate the way they ignore the sun? This mod makes it so that ALL mobs burn in daylight.",
            metadata={"metadata": {"description": "All hostile mobs burn during the day. Sun burns harder."}},
            long_description="# All Mobs Hate The SUN\n\nHostile mobs burn in daylight and take extra damage from the sun.",
        )
        self.assertEqual(
            repaired,
            "Hate when hostile mobs don't burn in daylight? Well this mod makes it so that ALL hostile mobs burn in the sun.",
        )

    def test_simplify_summary_candidate_rewrites_hopper_internal_jargon(self) -> None:
        simplified = simplify_summary_candidate(
            "Hate that hoppers move items too slowly? This mod speeds them up, automatically finishing the bulk transfer when the vanilla cooldown reaches 8.",
            metadata={"metadata": {"description": "Makes hoppers move items faster."}},
            long_description="# Instant Hoppers\n\nMakes hoppers move items faster.",
        )
        self.assertEqual(
            simplified,
            "Hate that hoppers move items too slowly? Well this mod makes hoppers move items faster automatically.",
        )

    def test_simplify_summary_candidate_prefers_full_stack_hopper_payoff_when_proven(self) -> None:
        simplified = simplify_summary_candidate(
            "Hate that hoppers move items too slowly? This mod makes hoppers move items faster automatically.",
            metadata={"metadata": {"description": "Makes hoppers move full stacks in one go."}},
            long_description="# Instant Hoppers\n\nHoppers can move full stacks in one go instead of one item at a time.",
        )
        self.assertEqual(
            simplified,
            "Hate that hoppers move items one by one? Well this mod makes hoppers move full stacks in one go.",
        )

    def test_simplify_summary_candidate_rewrites_question_only_tnt_summary(self) -> None:
        simplified = simplify_summary_candidate(
            "Wanting to have infinite TNT from dispensers?",
            metadata={"metadata": {"description": "Dispensers can fire TNT without consuming it."}},
            long_description="# TNT Duper\n\nDispensers can fire TNT without using up the TNT item.",
        )
        self.assertEqual(
            simplified,
            "Why look for TNT dupers when you could just build one with one dispenser and one tnt? No need for slime, no need for tutorials, no anything else then a dispenser and tnt.",
        )

    def test_summary_text_looks_glitched_flags_counting_output(self) -> None:
        self.assertTrue(summary_text_looks_glitched("Count: A(1) l2 l3 (space4"))
        self.assertFalse(summary_text_looks_glitched("Tired of mobs surviving daylight? Now every hostile mob burns in the sun."))

    def test_score_summary_candidate_prefers_hooky_player_facing_tone(self) -> None:
        hooky = "Hate that creeper that doesn't burn after day? This mod makes it so that ALL mobs burn in day."
        generic = "Hostile mobs ignite and take extra damage in daylight."
        self.assertGreater(score_summary_candidate(hooky), score_summary_candidate(generic))

    def test_score_summary_candidate_penalizes_clever_edgy_rewrites(self) -> None:
        example_like = (
            "Hate that creeper that doesn't burn after day? Hate the slimes that spawn in super flat worlds? "
            "This mod makes it so that ALL mobs burn in day."
        )
        edgy = "Tired of mobs roaming at night? They now literally burn in daylight, turning the sun into a free-range mob-killer."
        self.assertGreater(score_summary_candidate(example_like), score_summary_candidate(edgy))

    def test_score_summary_candidate_penalizes_invented_second_complaint(self) -> None:
        grounded = "Hate when hostile mobs survive daylight? This mod makes it so that ALL hostile mobs burn in the sun."
        invented = "Hate that mobs just wander in daylight? Hate the endless night raids? This mod makes it so that ALL hostile mobs burn in daylight."
        self.assertGreater(score_summary_candidate(grounded), score_summary_candidate(invented))

    def test_score_summary_candidate_prefers_player_problem_tnt_angle_over_literal_patch_notes(self) -> None:
        player_facing = (
            "Why look for TNT dupers when you could just build one with one dispenser and one tnt? "
            "No need for slime, no need for tutorials, no anything else then a dispenser and tnt."
        )
        literal = "Tired of dispensers using up your TNT? Well this mod makes dispensers fire TNT without consuming it."
        self.assertGreater(score_summary_candidate(player_facing), score_summary_candidate(literal))

    def test_choose_best_summary_candidate_prefers_hooky_option(self) -> None:
        best = choose_best_summary_candidate(
            [
                "Hostile mobs ignite and take extra damage in daylight.",
                "Hate that creeper that doesn't burn after day? This mod makes it so that ALL mobs burn in day.",
            ]
        )
        self.assertTrue(best.startswith("Hate that creeper"))

    def test_extract_best_answer_candidate_text_prefers_final_quoted_summary(self) -> None:
        extracted = extract_best_answer_candidate_text(
            """
            Let's count characters.
            Thus final answer: "Tired of mobs surviving daylight? Now every hostile mob ignites and takes extra damage when the sun shines."
            Check character count: 107.
            """
        )
        self.assertEqual(
            extracted,
            "Tired of mobs surviving daylight? Now every hostile mob ignites and takes extra damage when the sun shines.",
        )

    def test_parse_ai_summary_response_recovers_from_reasoning_counting_dump(self) -> None:
        parsed = parse_ai_summary_response(
            """
            Let's count characters.
            Thus final answer: "Tired of mobs surviving daylight? Now every hostile mob ignites and takes extra damage when the sun shines."
            Check character count: 107.
            """
        )
        self.assertTrue(parsed.startswith("Tired of mobs surviving daylight?"))

    def test_extract_answer_from_reasoning_text_prefers_final_json_object(self) -> None:
        extracted = extract_answer_from_reasoning_text(
            """
            I should return strict JSON.

            {
              "name": "All Mobs Hate The SUN",
              "long_description": "# All Mobs Hate The SUN\\n\\nEverything burns."
            }
            """
        )
        self.assertIn('"name": "All Mobs Hate The SUN"', extracted)

    def test_request_local_c05_chat_uses_reasoning_fallback_when_stream_has_no_content_events(self) -> None:
        lines = [
            '{"status":"start","request_id":"req-1"}\n',
            '{"event":"reasoning_text","content":"Reasoning: keep it short. Answer: {\\"name\\": \\"Demo\\", \\"long_description\\": \\"# Demo\\\\n\\\\nBody.\\"}"}\n',
            '{"status":"end","request_id":"req-1","reasoning_text":"Reasoning: keep it short. Answer: {\\"name\\": \\"Demo\\", \\"long_description\\": \\"# Demo\\\\n\\\\nBody.\\"}"}\n',
        ]

        class FakeResponse:
            def __enter__(self) -> "FakeResponse":
                return self

            def __exit__(self, exc_type, exc, tb) -> None:
                return None

            def __iter__(self):
                for line in lines:
                    yield line.encode("utf-8")

        with mock.patch("urllib.request.urlopen", return_value=FakeResponse()):
            response = request_local_c05_chat(
                base_url="http://localhost:8129",
                hoster="nvidia",
                model="nvidia/nemotron-3-nano-30b-a3b",
                user_prompt="Return strict JSON.",
                reasoning_effort="high",
                timeout_seconds=30,
                session_id="session-1",
                app_id="app-1",
            )

        self.assertIn('"name": "Demo"', response["text"])
        self.assertTrue(response["used_reasoning_fallback"])
        self.assertIn("Answer:", response["reasoning_text"])

    def test_request_local_c05_chat_recovers_missing_prefix_from_content_part_text(self) -> None:
        lines = [
            '{"status":"start","request_id":"req-1"}\n',
            '{"event":"content_part","content_type":"text","content":{"type":"text","text":"Hate that creeper that "},"request_id":"req-1"}\n',
            '{"event":"content","content":"doesn\'t burn after day? This mod makes it so that ALL mobs burn in day."}\n',
            '{"status":"end","request_id":"req-1"}\n',
        ]

        class FakeResponse:
            def __enter__(self) -> "FakeResponse":
                return self

            def __exit__(self, exc_type, exc, tb) -> None:
                return None

            def __iter__(self):
                for line in lines:
                    yield line.encode("utf-8")

        with mock.patch("urllib.request.urlopen", return_value=FakeResponse()):
            response = request_local_c05_chat(
                base_url="http://localhost:8129",
                hoster="nvidia",
                model="nvidia/nemotron-3-nano-30b-a3b",
                user_prompt="Return plain text.",
                reasoning_effort="high",
                timeout_seconds=30,
                session_id="session-1",
                app_id="app-1",
            )

        self.assertEqual(
            response["text"],
            "Hate that creeper that doesn't burn after day? This mod makes it so that ALL mobs burn in day.",
        )
        self.assertFalse(response["used_reasoning_fallback"])

    def test_request_local_c05_chat_prefers_reasoning_answer_when_content_is_clipped(self) -> None:
        lines = [
            '{"status":"start","request_id":"req-1"}\n',
            '{"event":"reasoning_text","content":"Final answer: Hate that mobs keep lurking in daylight? This mod makes it so that all hostile mobs burn in the sun."}\n',
            '{"event":"content","content":"lurking in daylight? This mod makes it so that all hostile mobs burn in the sun."}\n',
            '{"status":"end","request_id":"req-1","reasoning_text":"Final answer: Hate that mobs keep lurking in daylight? This mod makes it so that all hostile mobs burn in the sun."}\n',
        ]

        class FakeResponse:
            def __enter__(self) -> "FakeResponse":
                return self

            def __exit__(self, exc_type, exc, tb) -> None:
                return None

            def __iter__(self):
                for line in lines:
                    yield line.encode("utf-8")

        with mock.patch("urllib.request.urlopen", return_value=FakeResponse()):
            response = request_local_c05_chat(
                base_url="http://localhost:8129",
                hoster="nvidia",
                model="nvidia/nemotron-3-nano-30b-a3b",
                user_prompt="Return plain text.",
                reasoning_effort="high",
                timeout_seconds=30,
                session_id="session-1",
                app_id="app-1",
            )

        self.assertEqual(
            response["text"],
            "Hate that mobs keep lurking in daylight? This mod makes it so that all hostile mobs burn in the sun.",
        )

    def test_request_local_c05_chat_preserves_full_listing_field_block_when_reasoning_extractor_only_finds_tail(self) -> None:
        reasoning = (
            "name: TNT Duper\n"
            "long_description: # TNT Duper\n\nDispensers fire TNT without consuming the TNT item.\n"
            "categories: [\"technology\"]\n"
            "additional_categories: [\"utility\"]\n"
            "client_side: optional\n"
            "server_side: required\n"
            "license_id: (empty string)\n"
            "license_url:\n"
        )
        lines = [
            '{"status":"start","request_id":"req-1"}\n',
            ('{"event":"reasoning_text","content":' + json.dumps(reasoning) + '}\n'),
            ('{"status":"end","request_id":"req-1","reasoning_text":' + json.dumps(reasoning) + '}\n'),
        ]

        class FakeResponse:
            def __enter__(self) -> "FakeResponse":
                return self

            def __exit__(self, exc_type, exc, tb) -> None:
                return None

            def __iter__(self):
                for line in lines:
                    yield line.encode("utf-8")

        with mock.patch("urllib.request.urlopen", return_value=FakeResponse()):
            response = request_local_c05_chat(
                base_url="http://localhost:8129",
                hoster="nvidia",
                model="nvidia/nemotron-3-nano-30b-a3b",
                user_prompt="Return strict JSON.",
                reasoning_effort="high",
                timeout_seconds=30,
                session_id="session-1",
                app_id="app-1",
            )

        self.assertIn("name: TNT Duper", response["text"])
        self.assertIn("long_description:", response["text"])
        self.assertIn("license_url:", response["text"])
        self.assertTrue(response["used_reasoning_fallback"])

    def test_extract_json_object_reports_truncated_json_more_clearly(self) -> None:
        with self.assertRaises(ModCompilerError) as raised:
            extract_json_object(
                '{"name":"Demo","long_description":"# Demo\\n\\nBody","categories":["utility"],"additional_categories":["m'
            )
        self.assertIn("appears truncated", str(raised.exception))
        self.assertIn("--max-tokens 3200", str(raised.exception))

    def test_extract_json_object_repairs_missing_opening_brace(self) -> None:
        parsed = extract_json_object(
            '"name":"All Mobs Hate The Sun","long_description":"# All Mobs Hate The Sun\\n\\nBody.","categories":["game-mechanics"],"additional_categories":["mobs"],"client_side":"optional","server_side":"required","license_id":"","license_url":""}'
        )
        self.assertEqual(parsed["name"], "All Mobs Hate The Sun")
        self.assertEqual(parsed["server_side"], "required")

    def test_extract_json_object_prefers_full_outer_object_over_nested_object(self) -> None:
        parsed = extract_json_object(
            """
            "accent_color": "#ff7f2f",
            "background_image": "fire burning.jpg",
            "logo": {
              "eyebrow": "Sun Burn",
              "mark": "MH",
              "title": "Mobs Hate Sun"
            },
            "description": {
              "title": "All Mobs Hate The SUN",
              "chips": ["Daylight Trigger", "Unified Burn"]
            }
            }
            """
        )
        self.assertEqual(parsed["accent_color"], "#ff7f2f")
        self.assertEqual(parsed["logo"]["mark"], "MH")
        self.assertEqual(parsed["description"]["title"], "All Mobs Hate The SUN")

    def test_extract_json_object_repairs_missing_name_key_prefix(self) -> None:
        parsed = extract_json_object(
            'All Mobs Hate The Sun","long_description":"# All Mobs Hate The Sun\\n\\nBody.","categories":["mobs"],"additional_categories":["game-mechanics"],"client_side":"optional","server_side":"required","license_id":"","license_url":""}'
        )
        self.assertEqual(parsed["name"], "All Mobs Hate The Sun")
        self.assertEqual(parsed["categories"], ["mobs"])

    def test_extract_json_object_repairs_missing_name_key_with_quoted_value(self) -> None:
        parsed = extract_json_object(
            '"All Mobs Hate The Sun","long_description":"# All Mobs Hate The Sun\\n\\nBody.","categories":["mobs"],"additional_categories":["game-mechanics"],"client_side":"optional","server_side":"required","license_id":"","license_url":""}'
        )
        self.assertEqual(parsed["name"], "All Mobs Hate The Sun")
        self.assertEqual(parsed["server_side"], "required")

    def test_is_transient_github_cli_error_matches_connection_reset(self) -> None:
        self.assertTrue(is_transient_github_cli_error("read: connection reset by peer"))
        self.assertFalse(is_transient_github_cli_error("HTTP 403 permission denied"))

    def test_run_github_cli_retries_transient_failure(self) -> None:
        side_effects = [
            ModCompilerError("Command failed: gh workflow run\nread: connection reset by peer"),
            "ok\n",
        ]

        with mock.patch("modcompiler.auto_create_modrinth.run_subprocess", side_effect=side_effects) as runner:
            with mock.patch("modcompiler.auto_create_modrinth.time.sleep"):
                output = run_github_cli(["gh", "workflow", "run"], github_token="token", retries=2)

        self.assertEqual(output, "ok\n")
        self.assertEqual(runner.call_count, 2)

    def test_parse_visual_prompt_response_accepts_json(self) -> None:
        parsed = parse_visual_prompt_response(
            """
            {
              "accent_color": "#00d4ff",
              "logo": {
                "eyebrow": "Automation Class",
                "mark": "IH",
                "title": "Instant Hoppers",
                "subtitle": "Fast movement for item flow."
              },
              "description": {
                "kicker": "Utility Mod",
                "title": "Instant Hoppers",
                "tagline": "Move items faster with cleaner automation.",
                "chips": ["Fast Transfer", "Forge 1.12.2"],
                "stats": [
                  {"value": "5x", "label": "Transfer", "note": "Speeds up item movement."},
                  {"value": "Low", "label": "Setup", "note": "Easy to drop into redstone lines."}
                ]
              }
            }
            """
        )
        self.assertEqual(parsed["accent_color"], "#00d4ff")
        self.assertEqual(parsed["logo"]["mark"], "IH")
        self.assertEqual(parsed["description"]["chips"][0], "Fast Transfer")

    def test_parse_visual_prompt_response_repairs_missing_opening_brace(self) -> None:
        parsed = parse_visual_prompt_response(
            """
            "accent_color": "#ff7f2f",
            "background_image": "fire burning.jpg",
            "logo": {
              "eyebrow": "Sun Burn",
              "mark": "MH",
              "title": "Mobs Hate Sun"
            },
            "description": {
              "title": "All Mobs Hate The SUN",
              "chips": ["Daylight Trigger", "Unified Burn"]
            }
            }
            """
        )
        self.assertEqual(parsed["accent_color"], "#ff7f2f")
        self.assertEqual(parsed["logo"]["title"], "Mobs Hate Sun")
        self.assertEqual(parsed["description"]["chips"][1], "Unified Burn")

    def test_extract_aihorde_generated_images_finds_nested_url_and_base64_outputs(self) -> None:
        parsed = extract_aihorde_generated_images(
            {
                "id": "job-123",
                "status": {
                    "generations": [
                        {"image_url": "https://example.com/generated/logo.webp"},
                        {"img": "ZmFrZV9pbWFnZQ==", "mime_type": "image/webp"},
                    ]
                },
            }
        )
        self.assertEqual(parsed[0]["image_url"], "https://example.com/generated/logo.webp")
        self.assertEqual(parsed[1]["image_base64"], "ZmFrZV9pbWFnZQ==")
        self.assertEqual(parsed[1]["content_type"], "image/webp")

    def test_extract_aihorde_generated_images_finds_generate_image_and_wait_result(self) -> None:
        parsed = extract_aihorde_generated_images(
            {
                "operation": "generate_image_and_wait",
                "job_id": "job-456",
                "result": {
                    "status": {
                        "generations": [
                            {"image_url": "https://example.com/generated/banner.png", "content_type": "image/png"}
                        ]
                    }
                },
            }
        )
        self.assertEqual(parsed[0]["image_url"], "https://example.com/generated/banner.png")
        self.assertEqual(parsed[0]["content_type"], "image/png")

    def test_extract_aihorde_job_id_finds_nested_async_id(self) -> None:
        self.assertEqual(
            extract_aihorde_job_id({"result": {"id": "aihorde-job-123"}}),
            "aihorde-job-123",
        )

    def test_build_aihorde_image_submit_params_omits_model_by_default(self) -> None:
        payload = build_aihorde_image_submit_params(
            prompt="A clean graphic logo.",
            generation_params={"width": 1024, "height": 1024},
        )
        self.assertEqual(payload["prompt"], "A clean graphic logo.")
        self.assertNotIn("models", payload)
        self.assertEqual(payload["params"]["width"], 1024)

    def test_build_aihorde_image_submit_params_uses_models_array_when_forced(self) -> None:
        payload = build_aihorde_image_submit_params(
            prompt="A clean graphic logo.",
            image_model="some-aihorde-model",
        )
        self.assertEqual(payload["models"], ["some-aihorde-model"])

    def test_normalize_visual_prompt_text_keeps_one_short_sentence(self) -> None:
        prompt = normalize_visual_prompt_text(
            "A wide, cinematic banner for TNT Duper with the text 'TNT Duper' in huge letters, dramatic lighting, metallic textures, and more detail than needed. Second sentence."
        )
        self.assertTrue(prompt.endswith("."))
        self.assertLessEqual(len(prompt.split()), 24)
        self.assertNotIn("Second sentence", prompt)

    def test_decode_base64_image_payload_accepts_data_url(self) -> None:
        data, content_type = decode_base64_image_payload("data:image/png;base64,iVBORw0KGgo=")
        self.assertTrue(data.startswith(b"\x89PNG"))
        self.assertEqual(content_type, "image/png")

    def test_sanitize_sensitive_subprocess_text_redacts_github_auth_tokens(self) -> None:
        raw = (
            "git -c http.https://github.com/.extraheader=AUTHORIZATION: basic SECRET123 "
            "push origin HEAD:refs/heads/main github_pat_ABC123 x-access-token:ghp_secret"
        )
        cleaned = sanitize_sensitive_subprocess_text(raw)
        self.assertIn("basic <redacted>", cleaned)
        self.assertIn("github_pat_<redacted>", cleaned)
        self.assertIn("x-access-token:<redacted>", cleaned)
        self.assertNotIn("SECRET123", cleaned)
        self.assertNotIn("ghp_secret", cleaned)

    def test_detect_image_extension_from_bytes_handles_webp(self) -> None:
        self.assertEqual(
            detect_image_extension_from_bytes(b"RIFFabcdWEBPpayload", fallback="png"),
            "webp",
        )

    def test_build_ai_user_message_embeds_projectinfo_and_template_rules(self) -> None:
        prompt = build_ai_user_message(
            projectinfo_text="name=Demo\nloader=forge\n",
            source_verified_text="GUARANTEED PLAYER-VISIBLE FACTS\n- Demo feature.\n",
            source_context_text="# Original Source Context\n\n## Key Source Excerpts\n\nDemo source excerpt.\n",
            prompt_projectinfo_char_limit=5_000,
            template_examples={
                "modname": "Common Server Core",
                "summary": "Example summary",
                "description": "# Example\n\nExample long description.",
            },
        )
        self.assertIn("Return only valid JSON", prompt)
        self.assertIn("PROJECT INFO:", prompt)
        self.assertIn("name=Demo", prompt)
        self.assertIn("modname.txt", prompt)
        self.assertIn("Common Server Core", prompt)
        self.assertIn("Choose 1 to 3 most relevant primary categories", prompt)
        self.assertIn("These classifier hints are inferred from Modrinth's official category names", prompt)
        self.assertIn("- storage:", prompt)
        self.assertIn("license_id", prompt)
        self.assertIn("jar_display_name", prompt)
        self.assertIn("obvious typo", prompt.lower())
        self.assertNotIn("\"summary\"", prompt)
        self.assertIn("source-based understanding as the main truth", prompt)
        self.assertIn("simple everyday wording", prompt)
        self.assertIn("ORIGINAL SOURCE CONTEXT (DIRECT EXCERPTS FROM PROVIDED SRC):", prompt)
        self.assertIn("Demo source excerpt.", prompt)
        self.assertIn("SOURCE-VERIFIED NOTES FROM EARLIER IN THIS CHAT:", prompt)
        self.assertIn("Demo feature.", prompt)

    def test_build_source_clarification_context_uses_only_provided_source(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source_dir = root / "src"
            package_dir = source_dir / "main" / "java" / "com" / "example"
            package_dir.mkdir(parents=True, exist_ok=True)
            (package_dir / "DemoMod.java").write_text(
                "package com.example;\npublic class DemoMod { void run() {} }\n",
                encoding="utf-8",
            )

            context = build_source_clarification_context_text(source_dir=source_dir)

        self.assertIn("# Original Source Context", context)
        self.assertIn("provided_source_dir=", context)
        self.assertIn("DemoMod.java", context)
        self.assertNotIn("## Detected Metadata", context)
        self.assertNotIn("decompiled_dir=", context)

    def test_build_source_clarification_user_message_requests_simple_source_only_explanation(self) -> None:
        prompt = build_source_clarification_user_message(
            source_context_text="# Original Source Context\n\nsource_file_count=2\n",
            prompt_projectinfo_char_limit=5_000,
        )
        self.assertIn("original Minecraft mod source folder", prompt)
        self.assertIn("Use only the ORIGINAL SOURCE CONTEXT below.", prompt)
        self.assertIn("Do not use jar metadata, decompiled output", prompt)
        self.assertIn("Do not return JSON.", prompt)
        self.assertIn("Keep the wording plain and direct.", prompt)
        self.assertIn("GUARANTEED PLAYER-VISIBLE FACTS", prompt)
        self.assertIn("DO NOT CLAIM", prompt)
        self.assertIn("moves full stacks in one go", prompt)

    def test_build_effective_source_verified_text_merges_direct_source_hopper_notes(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source_dir = root / "src"
            source_dir.mkdir(parents=True, exist_ok=True)
            (source_dir / "Main.java").write_text(
                """
                public class Main {
                    void run() {
                        // Keep pushing until we can't push anymore
                        while(pushedThisLoop) {
                            ItemStack stackInHopper = hopperExtract.extractItem(i, 64, true);
                        }
                        // 3. PULL dropped items lying on top exactly like a hopper normally does, but instantly sweeping all items
                        List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(pos));
                        item.setDead();
                    }
                }
                """,
                encoding="utf-8",
            )

            verified = build_effective_source_verified_text(
                "GUARANTEED PLAYER-VISIBLE FACTS\n- Hoppers move items faster.\n\nDO NOT CLAIM\n- Adds commands.\n",
                source_dir=source_dir,
            )

        self.assertIn("Hoppers can move full stacks in one go instead of only one item at a time.", verified)
        self.assertIn("Hoppers also pull dropped items on top into themselves right away.", verified)
        self.assertIn("DO NOT CLAIM", verified)
        self.assertIn("Adds commands.", verified)

    def test_build_summary_user_message_is_summary_only_and_example_heavy(self) -> None:
        prompt = build_summary_user_message(
            projectinfo_text="name=Demo\nloader=forge\n",
            source_verified_text="GUARANTEED PLAYER-VISIBLE FACTS\n- Demo fact.\n",
            source_context_text="# Original Source Context\n\n## Key Source Excerpts\n\nDemo source excerpt.\n",
            name="Demo Mod",
            long_description="# Demo Mod\n\nExample body.",
            prompt_projectinfo_char_limit=5_000,
            template_examples={
                "modname": "Common Server Core",
                "summary": "Example summary one.\n\nExample summary two.",
                "description": "# Example\n\nExample long description.",
            },
        )
        self.assertIn("Return plain text only", prompt)
        self.assertIn("one strong player-facing summary line", prompt)
        self.assertIn("Example summary one.", prompt)
        self.assertIn("Demo Mod", prompt)
        self.assertIn("Write naturally", prompt)
        self.assertIn("Never count characters", prompt)
        self.assertIn("strongly prefer starting with a player frustration hook", prompt)
        self.assertIn("Avoid generic openings like", prompt)
        self.assertIn("Use that source-based understanding first", prompt)
        self.assertIn("Keep the wording simple and casual", prompt)
        self.assertIn("helper words and bridge phrases", prompt)
        self.assertIn("Slightly imperfect grammar is okay", prompt)
        self.assertIn("Never output template placeholders like `[problem]`", prompt)
        self.assertIn("copy an example structure almost directly", prompt)
        self.assertIn("strong style references, not a single fixed script", prompt)
        self.assertIn("Template A: Hate that", prompt)
        self.assertIn("Well this mod cancels that pain", prompt)
        self.assertIn("Good vs bad examples:", prompt)
        self.assertIn("Why look for TNT dupers", prompt)
        self.assertIn("Tired of dispensers using up your TNT?", prompt)
        self.assertIn("reads more like patch notes", prompt)
        self.assertIn("Do not invent a fake second complaint", prompt)
        self.assertIn("Do not swap in new problems like raids", prompt)
        self.assertIn("SPECIFIC GUIDANCE FOR THIS MOD:", prompt)
        self.assertIn("Use the template that best fits the concrete, proven annoyance", prompt)
        self.assertIn("ORIGINAL SOURCE CONTEXT (DIRECT EXCERPTS FROM PROVIDED SRC):", prompt)
        self.assertIn("Demo source excerpt.", prompt)
        self.assertIn("SOURCE-VERIFIED NOTES FROM EARLIER IN THIS CHAT:", prompt)
        self.assertIn("Demo fact.", prompt)

    def test_build_summary_correction_user_message_appends_requested_feedback(self) -> None:
        prompt = build_summary_correction_user_message("Original summary prompt.", "Hostile mobs burn in daylight.")
        self.assertIn("Previous generated summary:", prompt)
        self.assertIn("Hostile mobs burn in daylight.", prompt)
        self.assertIn("Wrong your generated summary is incorrect, see my instructions and examples again.", prompt)
        self.assertIn("Look back at the earlier history in this chat", prompt)
        self.assertIn("did not copy the example structure closely enough", prompt)
        self.assertIn("Only use things that the verified facts clearly prove", prompt)
        self.assertIn("Do not invent a fake second complaint", prompt)
        self.assertIn("Do not fall back to patch-note wording", prompt)
        self.assertIn("Do not repeat placeholder text like [problem]", prompt)
        self.assertIn("Here is the original summary task again.", prompt)
        self.assertIn("Original summary prompt.", prompt)
        self.assertIn("Use small helper words like well, so, or now", prompt)
        self.assertIn("Return only the corrected summary line.", prompt)

    def test_build_summary_specific_guidance_prefers_tnt_branch_even_if_verified_notes_mention_hoppers(self) -> None:
        guidance = build_summary_specific_guidance(
            name="TNT Duper",
            long_description="# TNT Duper\n\nDispensers duplicate TNT instead of consuming it.",
            projectinfo_text="name=TNT Duper\n",
            source_verified_text=(
                "GUARANTEED PLAYER-VISIBLE FACTS\n"
                "- Dispensers can fire TNT without using up the TNT item.\n"
                "- The duplicated TNT can be moved by pistons, collected by hoppers, etc., just like regular TNT.\n"
            ),
            source_context_text="BehaviorDispenseTNTDuper.java\nBlockDispenser\n",
        )
        self.assertIn("Why look for TNT dupers", guidance)
        self.assertIn("Bad example:", guidance)
        self.assertNotIn("hoppers move items", guidance)

    def test_build_visual_prompt_user_message_forbids_minecraft_visuals(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            background_dir = Path(temp_dir)
            (background_dir / "fresh spring.png").write_bytes(b"fake")
            (background_dir / "ocean.jpg").write_bytes(b"fake")
            choices = discover_background_images(background_dir)

            prompt = build_visual_prompt_user_message(
                listing={
                    "name": "All Most Hate The SUN",
                    "summary": "Hostile mobs burn in daylight.",
                    "long_description": "# All Most Hate The SUN\n\n## Description\n\nExample body.",
                },
                metadata={"metadata": {"name": "All Most Hate The SUN", "description": "Hostile mobs burn in daylight."}},
                template_examples=load_template_examples(REPO_ROOT / "templatecreatemod"),
                background_images=choices,
            )
            self.assertIn("accent_color", prompt)
            self.assertIn("background_image", prompt)
            self.assertIn("logo", prompt)
            self.assertIn("description", prompt)
            self.assertIn("approved examples", prompt.lower())
            self.assertIn("TNT Duper", prompt)
            self.assertIn("fresh spring.png", prompt)
            self.assertIn("ocean.jpg", prompt)

    def test_build_visual_prompt_user_message_includes_recommended_backgrounds(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            background_dir = Path(temp_dir)
            (background_dir / "fire burning.jpg").write_bytes(b"fake")
            (background_dir / "ocean.jpg").write_bytes(b"fake")
            (background_dir / "anime girl alya smiling.png").write_bytes(b"fake")
            choices = discover_background_images(background_dir)

            prompt = build_visual_prompt_user_message(
                listing={
                    "name": "All Mobs Hate The SUN",
                    "summary": "Hate when hostile mobs don't burn in daylight? Well this mod makes it so that ALL hostile mobs burn in the sun.",
                    "long_description": "Hostile mobs burn in daylight and catch fire while the sun is up.",
                },
                metadata={"metadata": {"name": "All Mobs Hate The SUN", "description": "Hostile mobs burn in daylight."}},
                template_examples={},
                background_images=choices,
            )
            self.assertIn("RECOMMENDED BACKGROUND OPTIONS", prompt)
            self.assertIn("fire burning.jpg", prompt)

    def test_recommend_background_images_prefers_fire_for_daylight_burn_mod(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            background_dir = Path(temp_dir)
            (background_dir / "fire burning.jpg").write_bytes(b"fake")
            (background_dir / "ocean.jpg").write_bytes(b"fake")
            (background_dir / "anime girl alya smiling.png").write_bytes(b"fake")
            choices = discover_background_images(background_dir)

            recommended = recommend_background_images(
                choices,
                title="All Mobs Hate The SUN",
                summary="Hate when hostile mobs don't burn in daylight? Well this mod makes it so that ALL hostile mobs burn in the sun.",
                long_description="Hostile mobs burn in daylight and catch fire while the sun is up.",
                limit=2,
            )
            self.assertEqual(recommended[0].relative_name, "fire burning.jpg")

    def test_normalize_background_choice_overrides_irrelevant_pick_when_better_match_exists(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            background_dir = Path(temp_dir)
            (background_dir / "fire burning.jpg").write_bytes(b"fake")
            (background_dir / "ocean.jpg").write_bytes(b"fake")
            (background_dir / "anime girl alya smiling.png").write_bytes(b"fake")
            choices = discover_background_images(background_dir)

            normalized = normalize_background_choice(
                "anime girl alya smiling.png",
                background_images=choices,
                title="All Mobs Hate The SUN",
                summary="Hate when hostile mobs don't burn in daylight? Well this mod makes it so that ALL hostile mobs burn in the sun.",
                long_description="Hostile mobs burn in daylight and catch fire while the sun is up.",
            )
            self.assertEqual(normalized, "fire burning.jpg")

    def test_generate_bundle_visual_assets_uses_local_fallback_when_visual_ai_response_is_unparseable(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            bundle_dir = root / "bundle"
            bundle_dir.mkdir(parents=True, exist_ok=True)
            background_dir = root / "backgrounds"
            background_dir.mkdir(parents=True, exist_ok=True)
            Image.new("RGB", (1600, 900), (180, 90, 40)).save(background_dir / "fire burning.jpg")
            choices = discover_background_images(background_dir)

            options = GenerateOptions(
                input_dir=root,
                output_dir=root / "out",
                manifest_path=MANIFEST_PATH,
                manifest={},
                c05_url="http://localhost:8129",
                c05_hoster="nvidia",
                c05_model="nvidia/nemotron-3-nano-30b-a3b",
                visual_c05_hoster="nvidia",
                visual_c05_model="nvidia/nemotron-3-nano-30b-a3b",
                reasoning_effort="high",
                temperature=0.2,
                max_tokens=3200,
                timeout_seconds=60,
                image_timeout_seconds=60,
                force=False,
                prompt_projectinfo_char_limit=24000,
                project_status="draft",
                version_status="listed",
                version_type="release",
                categories=[],
                additional_categories=[],
                client_side="optional",
                server_side="optional",
                nolinks=False,
                issues_url="",
                source_url="",
                wiki_url="",
                discord_url="",
                license_id="",
                license_url="",
                template_examples={},
                background_images=choices,
                github_token="",
                github_repo="",
                github_owner="",
                github_branch="main",
                remote_jar_paths={},
            )

            listing = {
                "name": "All Mobs Hate The SUN",
                "summary": "Hate when hostile mobs don't burn in daylight? Well this mod makes it so that ALL hostile mobs burn in the sun.",
                "long_description": "Hostile mobs burn in daylight. Water or fire immunity stops the effect. The damage happens every 20 ticks.",
            }
            metadata = {"metadata": {"name": "All Mobs Hate The SUN", "description": "Hostile mobs burn in daylight."}}

            def fake_render_asset(*, output_path_stem: Path, **_kwargs: object) -> dict[str, str]:
                path = output_path_stem.with_suffix(".webp")
                Image.new("RGB", (1024, 1024), (220, 120, 60)).save(path)
                return {"file": path.name}

            with mock.patch(
                "modcompiler.auto_create_modrinth.request_visual_design_response",
                return_value=({"text": "rail_left", "reasoning_text": "not json"}, "nvidia", "nvidia/nemotron-3-nano-30b-a3b"),
            ), mock.patch(
                "modcompiler.auto_create_modrinth.render_html_document_to_asset",
                side_effect=fake_render_asset,
            ), mock.patch(
                "modcompiler.auto_create_modrinth.render_html_document_to_image",
                return_value=Image.new("RGB", (1024, 1024), (200, 80, 60)),
            ), mock.patch(
                "modcompiler.auto_create_modrinth.prepare_generated_logo_icon",
                return_value=("icon.webp", ""),
            ):
                art_metadata = generate_bundle_visual_assets(
                    bundle_dir=bundle_dir,
                    listing=listing,
                    metadata=metadata,
                    options=options,
                )

            self.assertTrue(art_metadata["logo_file"])
            self.assertTrue(art_metadata["description_image_file"])
            self.assertEqual(art_metadata["background_source_name"], "fire burning.jpg")
            self.assertTrue(any("fallback" in warning.lower() for warning in art_metadata["warnings"]))

    def test_extract_html_document_fragments_pulls_style_and_body(self) -> None:
        css, body_html = extract_html_document_fragments(
            "<!doctype html><html><head><style>.hero{color:white;}</style></head><body><section>Banner</section></body></html>"
        )
        self.assertIn(".hero", css)
        self.assertEqual(body_html, "<section>Banner</section>")

    def test_build_visual_html_document_includes_local_background(self) -> None:
        document = build_visual_html_document(
            title="Strong Mobs",
            body_html="<div class='wordmark'>SM</div>",
            css=".wordmark { font-size: 120px; }",
            variant="description",
        )
        self.assertIn("background.jpg", document)
        self.assertIn("opacity: 0.80", document)
        self.assertIn("Strong Mobs", document)
        self.assertIn("width: 1600px;", document)
        self.assertIn("height: 900px;", document)
        self.assertIn("width: 1024px;", document)
        self.assertIn("height: 1024px;", document)
        self.assertIn("transform: translate(-50%, -50%) scale(", document)
        self.assertIn("transform: scale(1.18)", document)

    def test_inject_description_images_into_body_places_multiple_sections_under_heading(self) -> None:
        body = "# TNT Duper\n\n## Description\n\nExample body."
        updated = inject_description_images_into_body(
            body=body,
            title="TNT Duper",
            image_urls=["https://example.com/panel-1.webp", "https://example.com/panel-2.webp"],
        )
        self.assertIn("panel-1.webp", updated)
        self.assertIn("panel-2.webp", updated)
        self.assertTrue(updated.startswith("# TNT Duper\n\n![TNT Duper poster section 1 of 2]"))

    def test_strip_generated_description_images_removes_prior_generated_blocks(self) -> None:
        body = (
            "# TNT Duper\n\n"
            "![TNT Duper cover image](https://example.com/old.webp)\n\n"
            "## Description\n\n"
            "Example body."
        )
        cleaned = strip_generated_description_images(body=body, title="TNT Duper")
        self.assertNotIn("old.webp", cleaned)
        self.assertIn("## Description", cleaned)

    def test_save_vertical_poster_slices_splits_image_into_ordered_panels(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            image = Image.new("RGB", (1024, 1024), (20, 30, 40))
            file_names = save_vertical_poster_slices(
                image=image,
                output_dir=root,
                file_stem_prefix="description-image",
                max_slice_height=512,
            )
            self.assertEqual(file_names, ["description-image-01.webp", "description-image-02.webp"])
            with Image.open(root / file_names[0]) as first:
                self.assertEqual(first.size, (1024, 512))
            with Image.open(root / file_names[1]) as second:
                self.assertEqual(second.size, (1024, 512))

    def test_render_html_document_to_image_preserves_full_preview_frame(self) -> None:
        preview = Image.new("RGB", (1000, 1000), (20, 20, 20))
        for y in range(0, 160):
            for x in range(1000):
                preview.putpixel((x, y), (255, 0, 0))
        for y in range(840, 1000):
            for x in range(1000):
                preview.putpixel((x, y), (0, 0, 255))
        buffer = io.BytesIO()
        preview.save(buffer, format="PNG")
        buffer.seek(0)
        png_bytes = buffer.read()

        with mock.patch(
            "modcompiler.auto_create_modrinth.render_html_preview_via_qlmanage",
            return_value=png_bytes,
        ):
            rendered = render_html_document_to_image(
                html_path=Path("ignored.html"),
                timeout_seconds=60,
                target_size=(1600, 900),
            )

        self.assertEqual(rendered.size, (1600, 900))
        self.assertEqual(rendered.getpixel((799, 79)), (255, 0, 0))
        self.assertEqual(rendered.getpixel((799, 820)), (0, 0, 255))
        self.assertEqual(rendered.getpixel((80, 450)), (0, 0, 0))

    def test_load_template_examples_includes_visual_templates(self) -> None:
        examples = load_template_examples(REPO_ROOT / "templatecreatemod")
        self.assertIn("visual_logo_html", examples)
        self.assertIn("TNT Duper", examples["visual_logo_html"])
        self.assertIn("visual_description_html", examples)
        self.assertIn("Utility Mod", examples["visual_description_html"])

    def test_discover_background_images_scans_folder_and_ignores_hidden_files(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / ".DS_Store").write_bytes(b"ignored")
            (root / "fresh spring.png").write_bytes(b"fake")
            nested = root / "nested"
            nested.mkdir()
            (nested / "ocean.jpg").write_bytes(b"fake")

            choices = discover_background_images(root)

            self.assertEqual([choice.relative_name for choice in choices], ["fresh spring.png", "nested/ocean.jpg"])

    def test_copy_visual_background_asset_resizes_source_to_render_canvas(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source = root / "ocean.jpg"
            art_dir = root / "art"
            art_dir.mkdir()
            Image.new("RGB", (3200, 1800), (20, 80, 140)).save(source)

            output = copy_visual_background_asset(art_dir, source)

            self.assertEqual(output.suffix, ".webp")
            with Image.open(output) as prepared:
                self.assertEqual(prepared.size, (3072, 1728))

    def test_compose_description_poster_image_uses_background_for_side_regions(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            background_path = root / "background.webp"
            Image.new("RGB", (3072, 1728), (20, 60, 140)).save(background_path)
            stage = Image.new("RGB", (1024, 1024), (220, 80, 60))

            poster = compose_description_poster_image(
                background_path=background_path,
                stage_image=stage,
                target_size=(1600, 900),
            )

            self.assertEqual(poster.size, (1600, 900))
            self.assertEqual(poster.getpixel((800, 450)), (220, 80, 60))
            self.assertNotEqual(poster.getpixel((40, 450)), (0, 0, 0))

    def test_prepare_generated_logo_icon_stays_under_modrinth_limit(self) -> None:
        random.seed(7)
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            logo_path = root / "logo.png"
            image = Image.new("RGB", (1024, 1024))
            pixels = []
            for _y in range(1024):
                for _x in range(1024):
                    pixels.append(
                        (
                            random.randint(0, 255),
                            random.randint(0, 255),
                            random.randint(0, 255),
                        )
                    )
            image.putdata(pixels)
            image.save(logo_path)

            icon_name, warning = prepare_generated_logo_icon(root, logo_path)

            self.assertEqual(warning, "")
            self.assertTrue(icon_name.endswith(".webp"))
            self.assertLessEqual((root / icon_name).stat().st_size, 256 * 1024)

    def test_normalize_visual_design_response_uses_known_background_choice(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            (root / "sunset in grassfield.jpg").write_bytes(b"fake")
            (root / "fresh spring.png").write_bytes(b"fake")
            choices = discover_background_images(root)

            normalized = normalize_visual_design_response(
                {
                    "accent_color": "#ffaa33",
                    "background_image": "sunset in grassfield.jpg",
                    "logo": {"title": "TNT Duper"},
                    "description": {"title": "TNT Duper"},
                },
                title="TNT Duper",
                summary="A polished summary.",
                background_images=choices,
            )

            self.assertEqual(normalized["background_image"], "sunset in grassfield.jpg")

    def test_normalize_visual_design_response_replaces_admin_copy_with_feature_copy(self) -> None:
        normalized = normalize_visual_design_response(
            {
                "accent_color": "#ffaa33",
                "background_image": "background.jpg",
                "logo": {
                    "eyebrow": "Utility Mod",
                    "title": "All Mobs Hate The SUN",
                    "subtitle": "Hate when hostile mobs don't burn in daylight?",
                    "rail_left": "Draft Ready",
                    "rail_right": "Manual Review",
                },
                "description": {
                    "title": "All Mobs Hate The SUN",
                    "tagline": "Hate when hostile mobs don't burn in daylight?",
                    "chips": ["Draft Ready", "Manual Review"],
                    "stats": [
                        {
                            "value": "Ready",
                            "label": "Draft State",
                            "note": "Generated from the approved visual template.",
                        },
                        {
                            "value": "Review",
                            "label": "Next Step",
                            "note": "Open the draft on Modrinth and review the final layout.",
                        },
                    ],
                },
            },
            title="All Mobs Hate The SUN",
            summary="Hate when hostile mobs don't burn in daylight? Well this mod makes it so that ALL hostile mobs burn in the sun.",
            long_description=(
                "Hostile mobs burn in daylight. Water or fire immunity stops the effect. "
                "The damage happens every 20 ticks."
            ),
            background_images=(),
        )

        self.assertEqual(normalized["logo"]["eyebrow"], "Mob Tweaks")
        self.assertEqual(normalized["logo"]["rail_left"], "Daylight Burn")
        self.assertEqual(normalized["logo"]["rail_right"], "Hostile Mobs")
        self.assertEqual(normalized["description"]["chips"][:2], ["Daylight Burn", "Hostile Mobs"])
        self.assertEqual(normalized["description"]["stats"][0]["label"], "Affected Mobs")
        self.assertEqual(normalized["description"]["stats"][1]["label"], "Damage Tick")
        self.assertNotIn("draft", normalized["description"]["stats"][0]["note"].lower())

    def test_build_visual_prompt_user_message_bans_admin_copy(self) -> None:
        prompt = build_visual_prompt_user_message(
            listing={
                "name": "All Mobs Hate The SUN",
                "summary": "Hate when hostile mobs don't burn in daylight? Well this mod makes it so that ALL hostile mobs burn in the sun.",
                "long_description": "Hostile mobs burn in daylight.",
            },
            metadata={"metadata": {"name": "All Mobs Hate The SUN", "description": "Hostile mobs burn in daylight."}},
            template_examples={},
            background_images=(),
        )
        self.assertIn("Never mention draft status", prompt)
        self.assertIn("must describe actual mod behavior", prompt)

    def test_render_title_overlay_on_image_keeps_size_and_changes_pixels(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "logo.png"
            Image.new("RGBA", (512, 512), (100, 120, 140, 255)).save(path)
            before = path.read_bytes()
            render_title_overlay_on_image(image_path=path, title="TNT Duper")
            after = path.read_bytes()
            self.assertNotEqual(before, after)
            with Image.open(path) as result:
                self.assertEqual(result.size, (512, 512))

    def test_build_projectinfo_text_matches_template_style_more_closely(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            jar_path = root / "Demo-Mod.jar"
            jar_path.write_bytes(b"demo")
            source_root = root / "source" / "src" / "main" / "java" / "com" / "example"
            source_root.mkdir(parents=True, exist_ok=True)
            (source_root / "DemoMod.java").write_text(
                "package com.example;\npublic class DemoMod {}\n",
                encoding="utf-8",
            )
            decompiled_root = root / "decompiled" / "src" / "main" / "java" / "com" / "example"
            decompiled_root.mkdir(parents=True, exist_ok=True)
            (decompiled_root / "DemoMod.java").write_text(
                "package com.example;\npublic class DecompiledDemoMod {}\n",
                encoding="utf-8",
            )

            projectinfo = build_projectinfo_text(
                jar_path=jar_path,
                metadata={
                    "jar_name": "Demo-Mod.jar",
                    "loader": "forge",
                    "supported_minecraft": "1.20.1",
                    "primary_mod_id": "demo_mod",
                    "warnings": [],
                    "metadata": {
                        "name": "Demo Mod",
                        "mod_version": "1.0.0",
                        "description": "Example description",
                        "authors": ["Itamio"],
                        "license": "MIT",
                        "homepage": "",
                        "sources": "",
                        "issues": "",
                        "entrypoint_class": "com.example.DemoMod",
                        "group": "com.example",
                    },
                },
                source_dir=root / "source",
                decompiled_dir=root / "decompiled",
            )

            self.assertIn("source_origin=provided", projectinfo)
            self.assertIn("provided_source_dir=", projectinfo)
            self.assertIn("source_root=", projectinfo)
            self.assertIn("primary_mod_id=demo_mod", projectinfo)
            self.assertNotIn("requested_jar_path=", projectinfo)
            self.assertNotIn("metadata_source=", projectinfo)
            self.assertIn("DemoMod.java", projectinfo)
            self.assertNotIn("DecompiledDemoMod", projectinfo)

    def test_generate_bundle_uses_source_clarification_then_history_for_listing_and_summary(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            entry_dir = root / "upload"
            entry_dir.mkdir(parents=True, exist_ok=True)
            jar_path = entry_dir / "Demo-Mod.jar"
            jar_path.write_bytes(b"fake-jar")
            source_dir = entry_dir / "DemoSrc"
            package_dir = source_dir / "src" / "main" / "java" / "com" / "example"
            package_dir.mkdir(parents=True, exist_ok=True)
            (package_dir / "DemoMod.java").write_text(
                "package com.example;\npublic class DemoMod { void run() {} }\n",
                encoding="utf-8",
            )

            input_bundle = GenerateInputBundle(entry_dir=entry_dir, jar_path=jar_path, source_dir=source_dir)
            options = GenerateOptions(
                input_dir=root,
                output_dir=root / "out",
                manifest_path=MANIFEST_PATH,
                manifest={},
                c05_url="http://localhost:8129",
                c05_hoster="nvidia",
                c05_model="nvidia/nemotron-3-nano-30b-a3b",
                visual_c05_hoster="nvidia",
                visual_c05_model="nvidia/nemotron-3-nano-30b-a3b",
                reasoning_effort="high",
                temperature=0.2,
                max_tokens=3200,
                timeout_seconds=30,
                image_timeout_seconds=30,
                force=True,
                prompt_projectinfo_char_limit=10_000,
                project_status="draft",
                version_status="listed",
                version_type="release",
                categories=[],
                additional_categories=[],
                client_side="optional",
                server_side="required",
                nolinks=True,
                issues_url="",
                source_url="",
                wiki_url="",
                discord_url="",
                license_id="",
                license_url="",
                template_examples={
                    "modname": "Demo Mod",
                    "summary": "Tired of something? This mod fixes it.",
                    "description": "# Demo Mod\n\nExample description.",
                },
                background_images=(),
                github_token="",
                github_repo="Sekai0NI0itamio/ModCompiler",
                github_owner="Sekai0NI0itamio",
                github_branch="main",
                remote_jar_paths={},
            )

            metadata = {
                "jar_name": "Demo-Mod.jar",
                "loader": "forge",
                "supported_minecraft": "1.20.1",
                "primary_mod_id": "demo_mod",
                "warnings": [],
                "metadata": {
                    "name": "Demo Mod",
                    "description": "Example description",
                    "license": "",
                    "entrypoint_class": "com.example.DemoMod",
                },
            }

            stream_calls: list[dict[str, object]] = []

            def fake_stream_local_c05_chat(**kwargs):
                stream_calls.append(kwargs)
                prompt = str(kwargs.get("user_prompt", ""))
                if "original Minecraft mod source folder" in prompt:
                    return "- This mod makes hostile mobs burn in the sun.\n- It appears to focus on daytime sunlight."
                if "Return only valid JSON" in prompt:
                    return (
                        '{"name":"Demo Mod","long_description":"# Demo Mod\\n\\n'
                        'Tired of hostile mobs surviving daylight? This mod makes them burn in the sun.",'
                        '"categories":["mobs"],"additional_categories":["game-mechanics"],'
                        '"client_side":"optional","server_side":"required","license_id":"","license_url":""}'
                    )
                return "Tired of hostile mobs surviving daylight? This mod makes them burn in the sun."

            with mock.patch("modcompiler.auto_create_modrinth.inspect_mod_jar", return_value=metadata):
                with mock.patch("modcompiler.auto_create_modrinth.remote_decompile_jar_via_github_actions", return_value=metadata):
                    with mock.patch("modcompiler.auto_create_modrinth.stream_local_c05_chat", side_effect=fake_stream_local_c05_chat):
                        with mock.patch(
                            "modcompiler.auto_create_modrinth.generate_bundle_visual_assets",
                            return_value={
                                "generated_at": "2026-03-30T00:00:00Z",
                                "render_engine": "test",
                                "render_timeout_seconds": 30,
                                "background_file": "",
                                "logo_file": "",
                                "description_image_file": "",
                                "icon_file": "",
                                "warnings": [],
                            },
                        ):
                            result = generate_bundle_for_input_bundle(input_bundle, options)

            self.assertEqual(result["status"], "ready_for_verification")
            self.assertEqual(len(stream_calls), 4)
            self.assertEqual(stream_calls[0]["system_prompt"], "")
            self.assertTrue(bool(stream_calls[0].get("include_history", False)))
            self.assertEqual(stream_calls[0]["max_tokens"], None)
            self.assertIn("ORIGINAL SOURCE CONTEXT", str(stream_calls[0]["user_prompt"]))
            self.assertEqual(stream_calls[0]["app_id"], "auto-create-modrinth-text")
            self.assertEqual(stream_calls[1]["system_prompt"], "")
            self.assertEqual(stream_calls[1]["include_history"], True)
            self.assertEqual(stream_calls[1]["max_tokens"], None)
            self.assertIn("Return only valid JSON", str(stream_calls[1]["user_prompt"]))
            self.assertIn("ORIGINAL SOURCE CONTEXT (DIRECT EXCERPTS FROM PROVIDED SRC):", str(stream_calls[1]["user_prompt"]))
            self.assertIn("SOURCE-VERIFIED NOTES FROM EARLIER IN THIS CHAT:", str(stream_calls[1]["user_prompt"]))
            self.assertEqual(stream_calls[1]["app_id"], "auto-create-modrinth-text")
            self.assertEqual(stream_calls[2]["include_history"], True)
            self.assertIn("Write only the Modrinth summary", str(stream_calls[2]["user_prompt"]))
            self.assertIn("ORIGINAL SOURCE CONTEXT (DIRECT EXCERPTS FROM PROVIDED SRC):", str(stream_calls[2]["user_prompt"]))
            self.assertIn("SOURCE-VERIFIED NOTES FROM EARLIER IN THIS CHAT:", str(stream_calls[2]["user_prompt"]))
            self.assertEqual(stream_calls[2]["app_id"], "auto-create-modrinth-text")
            self.assertEqual(stream_calls[3]["include_history"], True)
            self.assertIn("Wrong your generated summary is incorrect", str(stream_calls[3]["user_prompt"]))
            self.assertEqual(stream_calls[3]["app_id"], "auto-create-modrinth-text")

            bundle_dir = options.output_dir / input_bundle.bundle_slug
            self.assertTrue((bundle_dir / "ai_clarification_request_user_message.txt").exists())
            self.assertTrue((bundle_dir / "ai_clarification_response.txt").exists())
            self.assertTrue((bundle_dir / "source_clarification_context.txt").exists())
            self.assertTrue((bundle_dir / "source_verified_notes.txt").exists())

    def test_inject_description_image_into_body_places_image_under_title(self) -> None:
        body = "# All Most Hate The SUN\n\n## Description\n\nExample paragraph."
        updated = inject_description_image_into_body(
            body=body,
            title="All Most Hate The SUN",
            image_url="https://cdn.modrinth.com/data/example/images/cover.png",
        )
        self.assertIn("# All Most Hate The SUN\n\n![All Most Hate The SUN cover image]", updated)
        self.assertIn("## Description", updated)

    def test_normalize_short_description_keeps_hook_style_when_under_new_limit(self) -> None:
        text = (
            "Hate when mobs survive daylight? This mod makes every hostile mob burn in the sun."
        )
        normalized = normalize_short_description(text, fallback="A Minecraft mod.")
        self.assertEqual(normalized, text)

    def test_finalize_generated_listing_filters_technical_copy(self) -> None:
        listing = finalize_generated_listing(
            listing={
                "name": "Instant Hoppers",
                "long_description": (
                    "# Instant Hoppers\n\n"
                    "Instant Hoppers removes the usual delay from hopper transfers. "
                    "It uses reflection to access the hopper's internal cooldown field. "
                    "This is a Forge mod for Minecraft 1.12.2."
                ),
            },
            summary_text="Makes hoppers transfer items instantly instead of waiting for cooldowns.",
            metadata={
                "metadata": {
                    "name": "Instant Hoppers",
                    "mod_id": "instant_hoppers",
                    "description": "Makes hoppers transfer items instantly.",
                }
            },
            jar_path=Path("Instant-Hoppers.jar"),
        )

        self.assertEqual(listing["name"], "Instant Hoppers")
        self.assertIn("hoppers move items faster automatically", listing["summary"].lower())
        self.assertNotIn("reflection", listing["long_description"].lower())
        self.assertNotIn("forge mod", listing["long_description"].lower())
        self.assertIn("hopper", listing["long_description"].lower())
        self.assertIn("# Instant Hoppers", listing["long_description"])

    def test_finalize_generated_listing_filters_generic_filler_copy(self) -> None:
        listing = finalize_generated_listing(
            listing={
                "name": "All Mobs Hate The SUN",
                "long_description": (
                    "# All Mobs Hate The SUN\n\n"
                    "This mod makes hostile mobs burn in daylight.\n\n"
                    "- Works in both single-player and multiplayer worlds.\n"
                    "- Works on any world, including existing saves.\n"
                    "- The mod is lightweight and only requires Forge 1.12.2.\n"
                    "- Makes daylight dangerous for mobs."
                ),
            },
            summary_text="Hate when hostile mobs survive daylight? This mod makes it so that ALL hostile mobs burn in the sun.",
            metadata={"metadata": {"name": "All Mobs Hate The SUN"}},
            jar_path=Path("All-Mobs-Hate-The-SUN.jar"),
        )

        lowered = listing["long_description"].lower()
        self.assertNotIn("single-player and multiplayer", lowered)
        self.assertNotIn("existing saves", lowered)
        self.assertNotIn("lightweight", lowered)
        self.assertIn("daylight dangerous for mobs", lowered)

    def test_finalize_generated_listing_restructures_plain_long_description_for_readability(self) -> None:
        listing = finalize_generated_listing(
            listing={
                "name": "Instant Hoppers",
                "long_description": (
                    "# Instant Hoppers\n\n"
                    "Instant Hoppers speeds up how quickly hoppers move items into adjacent containers. "
                    "When a hopper finishes a transfer and its internal cooldown reaches 8, the mod automatically pushes the rest of the items in bulk, so items move faster without any player action.\n\n"
                    "The mod does not add new blocks, items, or recipes. It works in any world where it is loaded and runs on the server side, so it affects all players in that world.\n\n"
                    "No commands are required; the change happens automatically in the background."
                ),
            },
            summary_text="Hate that hoppers move items too slowly? This mod speeds them up, automatically finishing the bulk transfer when the vanilla cooldown reaches 8.",
            metadata={"metadata": {"name": "Instant Hoppers", "description": "Makes hoppers move items faster."}},
            jar_path=Path("Instant-Hoppers.jar"),
            verified_facts_text=(
                "GUARANTEED PLAYER-VISIBLE FACTS\n"
                "- Hoppers move items into adjacent containers faster, finishing a bulk transfer when the vanilla cooldown reaches 8.\n"
                "- The mod works automatically; no commands or player actions are required.\n"
                "- The mod does not add new blocks, items, or crafting recipes.\n"
            ),
        )

        lowered = listing["long_description"].lower()
        self.assertIn("## What It Does", listing["long_description"])
        self.assertIn("Hoppers move items into connected containers faster.", listing["long_description"])
        self.assertNotIn("cooldown reaches 8", lowered)
        self.assertNotIn("works in any world where it is loaded", lowered)
        self.assertNotIn("runs on the server side", lowered)

    def test_finalize_generated_listing_enriches_hopper_description_with_missing_verified_facts(self) -> None:
        listing = finalize_generated_listing(
            listing={
                "name": "Instant Hoppers",
                "long_description": (
                    "# Instant Hoppers\n\n"
                    "Instant Hoppers makes hoppers move items to the block they face faster.\n\n"
                    "## What you'll notice\n"
                    "- Hoppers push items into adjacent chests, furnaces, or other containers more quickly.\n"
                ),
            },
            summary_text="Hate that hoppers move items one by one? Well this mod makes hoppers move full stacks in one go.",
            metadata={"metadata": {"name": "Instant Hoppers", "description": "Makes hoppers move full stacks in one go."}},
            jar_path=Path("Instant-Hoppers.jar"),
            verified_facts_text=(
                "GUARANTEED PLAYER-VISIBLE FACTS\n"
                "- Hoppers can move full stacks in one go instead of only one item at a time.\n"
                "- Hoppers also pull dropped items on top into themselves right away.\n"
                "\n"
                "DO NOT CLAIM\n"
                "- Adds commands.\n"
            ),
        )

        self.assertIn("full stacks in one go", listing["long_description"].lower())
        self.assertIn("dropped items on top", listing["long_description"].lower())

    def test_finalize_generated_listing_recovers_from_malformed_clarification_header_and_placeholder_body(self) -> None:
        listing = finalize_generated_listing(
            listing={
                "name": "Instant Hoppers",
                "long_description": "# Instant Hoppers\n\n...",
            },
            summary_text="Hate that hoppers move items too slowly? Well this mod makes hoppers move items faster automatically.",
            metadata={"metadata": {"name": "Instant Hoppers", "description": "Makes hoppers move items faster."}},
            jar_path=Path("Instant-Hoppers.jar"),
            verified_facts_text=(
                "UARANTEED PLAYER-VISIBLE FACTS\n"
                "- Hoppers transfer items faster than vanilla because they skip the 8-tick cooldown.\n"
                "- When a hopper finishes a transfer it immediately tries to move more items in the same tick.\n"
                "- The mod works automatically; no player action or command is required.\n"
                "- The changes only affect hopper tile entities; no new blocks or items are added.\n"
                "- Hoppers can pull items from above and push items to below in the same transfer step.\n"
                "\n"
                "DO NOT CLAIM- The mod adds any new crafting recipes.\n"
            ),
        )

        lowered = listing["long_description"].lower()
        self.assertIn("## What It Does", listing["long_description"])
        self.assertIn("Hoppers move items into connected containers faster.", listing["long_description"])
        self.assertNotIn("...", listing["long_description"])
        self.assertNotIn("8-tick cooldown", lowered)
        self.assertNotIn("same tick", lowered)
        self.assertNotIn("no player action or command is required", lowered)

    def test_finalize_generated_listing_filters_unverified_claims_against_verified_notes(self) -> None:
        listing = finalize_generated_listing(
            listing={
                "name": "All Mobs Hate The SUN",
                "long_description": (
                    "# All Mobs Hate The SUN\n\n"
                    "- Hostile mobs burn in daylight.\n"
                    "- The effect works even if they wear a helmet.\n"
                    "- The damage only applies in the Overworld, Nether, or End.\n"
                    "- No new items, blocks, or commands are added."
                ),
            },
            summary_text="Hate when hostile mobs don't burn in daylight? Well this mod makes it so that ALL hostile mobs burn in the sun.",
            metadata={"metadata": {"name": "All Mobs Hate The SUN"}},
            jar_path=Path("All-Mobs-Hate-The-SUN.jar"),
            verified_facts_text=(
                "GUARANTEED PLAYER-VISIBLE FACTS\n"
                "- When it’s daytime and a hostile mob is out in the open, it catches fire and loses 3 hearts.\n"
                "- No new items, blocks, or commands are added.\n"
            ),
        )

        lowered = listing["long_description"].lower()
        self.assertIn("hostile mobs burn in daylight", lowered)
        self.assertIn("no new items, blocks, or commands are added", lowered)
        self.assertNotIn("helmet", lowered)
        self.assertNotIn("overworld", lowered)
        self.assertNotIn("nether", lowered)

    def test_strip_empty_markdown_heading_blocks_removes_trailing_empty_heading(self) -> None:
        cleaned = strip_empty_markdown_heading_blocks(
            "# Title\n\n## What it does\n\n- Example bullet.\n\n## Compatibility"
        )
        self.assertNotIn("## Compatibility", cleaned)
        self.assertIn("## What it does", cleaned)

    def test_finalize_generated_listing_prefers_jar_spelling_for_obvious_typo(self) -> None:
        listing = finalize_generated_listing(
            listing={
                "name": "Strong Most",
                "long_description": "# Strong Most\n\n## Description\n\nMakes hostile mobs more dangerous.",
            },
            summary_text="Makes mobs hit harder.",
            metadata={
                "metadata": {
                    "name": "Strong Most",
                    "mod_id": "strong_most",
                    "description": "Makes hostile mobs more dangerous.",
                }
            },
            jar_path=Path("strong-mobs.jar"),
        )

        self.assertEqual(listing["name"], "Strong Mobs")
        self.assertTrue(listing["long_description"].startswith("# Strong Mobs"))

    def test_compute_logo_layout_variables_expands_for_long_titles(self) -> None:
        variables = compute_logo_layout_variables("An Extremely Long Mod Title That Needs Extra Room")
        self.assertEqual(variables["--frame-inset"], "26px")
        self.assertEqual(variables["--title-size"], "44px")

    def test_compute_description_layout_variables_shrinks_title_for_long_titles(self) -> None:
        variables = compute_description_layout_variables("An Extremely Long Mod Title That Needs Extra Room")
        self.assertEqual(variables["--side-width"], "188px")
        self.assertEqual(variables["--description-title-size"], "62px")

    def test_is_bundle_verified_requires_verified_first_non_comment_line(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            verify_path = Path(temp_dir) / "verify.txt"
            verify_path.write_text("# comment\n\nverified\n", encoding="utf-8")
            self.assertTrue(is_bundle_verified(verify_path))
            verify_path.write_text("pending\nverified\n", encoding="utf-8")
            self.assertFalse(is_bundle_verified(verify_path))

    def test_is_bundle_approved_for_draft_honors_verified_override(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            verify_path = Path(temp_dir) / "verify.txt"
            verify_path.write_text("pending\n", encoding="utf-8")
            self.assertFalse(is_bundle_approved_for_draft(verify_path, assume_verified=False))
            self.assertTrue(is_bundle_approved_for_draft(verify_path, assume_verified=True))

    def test_set_verify_file_verified_replaces_first_decision_line_and_preserves_rest(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            verify_path = Path(temp_dir) / "verify.txt"
            verify_path.write_text(
                "# comment\n\npending\n\nReplace the first line with:\nverified\n",
                encoding="utf-8",
            )
            set_verify_file_verified(verify_path)
            self.assertTrue(is_bundle_verified(verify_path))
            content = verify_path.read_text(encoding="utf-8")
            self.assertIn("# comment", content)
            self.assertIn("Replace the first line with:", content)

    def test_summarize_result_counts_tracks_submitted_separately_from_skipped(self) -> None:
        counts = summarize_result_counts(
            [
                {"status": "draft_created"},
                {"status": "submitted"},
                {"status": "skipped"},
                {"status": "failed"},
            ]
        )
        self.assertEqual(counts["created"], 1)
        self.assertEqual(counts["submitted"], 1)
        self.assertEqual(counts["skipped"], 1)
        self.assertEqual(counts["failed"], 1)

    def test_expand_supported_versions_uses_manifest_supported_versions(self) -> None:
        manifest = load_json(MANIFEST_PATH)
        versions = expand_supported_versions(
            manifest,
            {
                "loader": "forge",
                "resolved_range_folders": ["1.18-1.18.2"],
                "supported_minecraft": "[1.18,1.19)",
            },
        )
        self.assertEqual(versions, ["1.18", "1.18.1", "1.18.2"])

    def test_discover_generate_input_bundles_requires_one_jar_and_one_source_folder(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            bundle_one = root / "1"
            bundle_one.mkdir()
            (bundle_one / "All-Mobs-Hate-The-SUN.jar").write_bytes(b"jar")
            (bundle_one / "All-Most-Hate-The-SUN" / "src" / "main" / "java").mkdir(parents=True)
            (bundle_one / "All-Most-Hate-The-SUN" / "src" / "main" / "java" / "Demo.java").write_text(
                "class Demo {}",
                encoding="utf-8",
            )

            bundles = discover_generate_input_bundles(root)

            self.assertEqual(len(bundles), 1)
            self.assertEqual(bundles[0].jar_path.name, "All-Mobs-Hate-The-SUN.jar")
            self.assertEqual(bundles[0].source_dir.name, "All-Most-Hate-The-SUN")

    def test_filter_generate_input_bundles_matches_bundle_slug_stem_and_source_folder(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            bundle_one = root / "1"
            bundle_two = root / "2"
            bundle_one.mkdir()
            bundle_two.mkdir()
            (bundle_one / "All Mobs Hate The SUN.jar").write_bytes(b"jar")
            (bundle_two / "TNT Duper.jar").write_bytes(b"jar")
            (bundle_one / "All-Most-Hate-The-SUN" / "src").mkdir(parents=True)
            (bundle_one / "All-Most-Hate-The-SUN" / "src" / "Demo.java").write_text("class Demo {}", encoding="utf-8")
            (bundle_two / "TNT-Duper" / "src").mkdir(parents=True)
            (bundle_two / "TNT-Duper" / "src" / "Demo.java").write_text("class Demo {}", encoding="utf-8")
            bundles = discover_generate_input_bundles(root)

            selected_by_slug = filter_generate_input_bundles(
                bundles=bundles,
                only_bundle="all-mobs-hate-the-sun",
                input_dir=root,
            )
            selected_by_stem = filter_generate_input_bundles(
                bundles=bundles,
                only_bundle="All Mobs Hate The SUN",
                input_dir=root,
            )
            selected_by_source = filter_generate_input_bundles(
                bundles=bundles,
                only_bundle="All-Most-Hate-The-SUN",
                input_dir=root,
            )

            self.assertEqual([bundle.jar_path.name for bundle in selected_by_slug], ["All Mobs Hate The SUN.jar"])
            self.assertEqual([bundle.jar_path.name for bundle in selected_by_stem], ["All Mobs Hate The SUN.jar"])
            self.assertEqual([bundle.jar_path.name for bundle in selected_by_source], ["All Mobs Hate The SUN.jar"])

    def test_filter_generate_input_bundles_errors_with_available_bundle_slugs(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            bundle_one = root / "1"
            bundle_two = root / "2"
            bundle_one.mkdir()
            bundle_two.mkdir()
            (bundle_one / "All Mobs Hate The SUN.jar").write_bytes(b"jar")
            (bundle_two / "TNT Duper.jar").write_bytes(b"jar")
            (bundle_one / "All-Most-Hate-The-SUN" / "src").mkdir(parents=True)
            (bundle_one / "All-Most-Hate-The-SUN" / "src" / "Demo.java").write_text("class Demo {}", encoding="utf-8")
            (bundle_two / "TNT-Duper" / "src").mkdir(parents=True)
            (bundle_two / "TNT-Duper" / "src" / "Demo.java").write_text("class Demo {}", encoding="utf-8")
            bundles = discover_generate_input_bundles(root)

        with self.assertRaises(ModCompilerError) as context:
            filter_generate_input_bundles(
                bundles=bundles,
                only_bundle="instant-hoppers-1.0",
                input_dir=root,
            )

        self.assertIn("instant-hoppers-1.0", str(context.exception))
        self.assertIn("all-mobs-hate-the-sun", str(context.exception))
        self.assertIn("tnt-duper", str(context.exception))

    def test_build_project_and_version_drafts_use_listing_and_defaults(self) -> None:
        class Options:
            categories = []
            additional_categories = ["utility"]
            client_side = "optional"
            server_side = "optional"
            project_status = "draft"
            version_status = "draft"
            version_type = "release"
            issues_url = ""
            source_url = ""
            wiki_url = ""
            discord_url = ""
            license_id = ""
            license_url = ""
            manifest = load_json(MANIFEST_PATH)

        metadata = {
            "loader": "fabric",
            "supported_minecraft": "1.21.1",
            "resolved_range_folders": ["1.21-1.21.1"],
            "metadata": {
                "mod_id": "allowdisconnect",
                "name": "Allow Disconnect",
                "mod_version": "1.0.0",
                "description": "Example",
                "authors": ["Itamio"],
                "license": "MIT",
                "homepage": "",
                "sources": "",
                "issues": "",
                "entrypoint_class": "com.example.AllowDisconnect",
                "group": "com.example",
            },
        }
        listing = {
            "name": "Allow Disconnect",
            "short_description": "Keep multiplayer sessions alive while you browse menus.",
            "long_description": "Markdown body",
        }

        project = build_project_draft(metadata=metadata, listing=listing, options=Options())
        version = build_version_draft(metadata=metadata, listing=listing, options=Options())

        self.assertEqual(project["slug"], "allow-disconnect")
        self.assertEqual(project["categories"], ["utility"])
        self.assertEqual(project["additional_categories"], [])
        self.assertEqual(project["license_id"], "MIT")
        self.assertEqual(version["loaders"], ["fabric"])
        self.assertEqual(version["version_number"], "1.0.0")
        self.assertIn("1.21.1", version["game_versions"])

    def test_build_project_draft_uses_ai_generated_categories_and_environment(self) -> None:
        class Options:
            categories = []
            additional_categories = []
            client_side = "optional"
            server_side = "optional"
            project_status = "draft"
            version_status = "draft"
            version_type = "release"
            issues_url = ""
            source_url = ""
            wiki_url = ""
            discord_url = ""
            license_id = ""
            license_url = ""
            manifest = load_json(MANIFEST_PATH)

        metadata = {
            "loader": "forge",
            "supported_minecraft": "1.12.2",
            "resolved_range_folders": ["1.12-1.12.2"],
            "metadata": {
                "mod_id": "servercore",
                "name": "ServerCore",
                "mod_version": "1.0.0",
                "description": "Teleport utilities.",
                "authors": ["Itamio"],
                "license": "",
                "homepage": "",
                "sources": "",
                "issues": "",
                "entrypoint_class": "",
                "group": "",
            },
        }
        listing = {
            "name": "ServerCore",
            "short_description": "Teleport utilities in one mod.",
            "long_description": "# ServerCore\n\nA polished description.",
        }

        project = build_project_draft(
            metadata=metadata,
            listing=listing,
            options=Options(),
            ai_result={
                "categories": ["Utility", "Management", "Social"],
                "additional_categories": [],
                "client_side": "optional",
                "server_side": "required",
                "license_id": "",
                "license_url": "",
            },
        )

        self.assertEqual(project["categories"], ["utility", "management", "social"])
        self.assertEqual(project["server_side"], "required")

    def test_build_version_draft_includes_environment_metadata_for_server_side_world_mod(self) -> None:
        class Options:
            categories = []
            additional_categories = []
            client_side = "optional"
            server_side = "optional"
            project_status = "draft"
            version_status = "draft"
            version_type = "release"
            issues_url = ""
            source_url = ""
            wiki_url = ""
            discord_url = ""
            license_id = ""
            license_url = ""
            manifest = load_json(MANIFEST_PATH)

        metadata = {
            "loader": "forge",
            "supported_minecraft": "1.12.2",
            "resolved_range_folders": ["1.12-1.12.2"],
            "metadata": {
                "mod_id": "instanthoppers",
                "name": "Instant Hoppers",
                "mod_version": "1.0.0",
                "description": "Hoppers move whole stacks in one go.",
                "authors": ["Itamio"],
                "license": "",
                "homepage": "",
                "sources": "",
                "issues": "",
                "entrypoint_class": "",
                "group": "",
            },
        }
        listing = {
            "name": "Instant Hoppers",
            "summary": "Hate that hoppers move items too slowly? Well this mod makes hoppers move full stacks in one go.",
            "long_description": "# Instant Hoppers\n\nMoves full stacks in one go.",
        }

        version = build_version_draft(
            metadata=metadata,
            listing=listing,
            options=Options(),
            ai_result={
                "client_side": "optional",
                "server_side": "required",
            },
        )

        self.assertEqual(version["client_side"], "optional")
        self.assertEqual(version["server_side"], "required")
        self.assertEqual(version["environment_group"], "server_side_only")
        self.assertEqual(version["environment_label"], "Server-side only")
        self.assertEqual(version["environment_option"], "works_in_singleplayer_too")
        self.assertEqual(version["environment_option_label"], "Works in singleplayer too")
        self.assertTrue(version["works_in_singleplayer"])
        self.assertFalse(version["dedicated_server_only"])

    def test_build_project_draft_limits_primary_categories_to_three(self) -> None:
        class Options:
            categories = []
            additional_categories = []
            client_side = "optional"
            server_side = "optional"
            project_status = "approved"
            version_status = "listed"
            version_type = "release"
            issues_url = ""
            source_url = ""
            wiki_url = ""
            discord_url = ""
            license_id = ""
            license_url = ""
            manifest = load_json(MANIFEST_PATH)

        metadata = {
            "loader": "forge",
            "supported_minecraft": "1.12.2",
            "resolved_range_folders": ["1.12-1.12.2"],
            "metadata": {
                "mod_id": "sunburnmobs",
                "name": "Sunburn Mobs",
                "mod_version": "1.0.0",
                "description": "Example",
                "authors": ["Itamio"],
                "license": "",
                "homepage": "",
                "sources": "",
                "issues": "",
                "entrypoint_class": "",
                "group": "",
            },
        }
        listing = {
            "name": "Sunburn Mobs",
            "short_description": "Example summary",
            "long_description": "# Sunburn Mobs\n\nExample body.",
        }

        project = build_project_draft(metadata=metadata, listing=listing, options=Options())

        self.assertEqual(project["status"], "draft")
        self.assertEqual(project["categories"], ["mobs"])
        self.assertEqual(project["additional_categories"], [])

        project = build_project_draft(
            metadata=metadata,
            listing=listing,
            options=Options(),
            ai_result={
                "categories": ["utility", "management", "social", "storage", "technology"],
                "additional_categories": ["adventure", "game-mechanics"],
            },
        )

        self.assertEqual(project["status"], "draft")
        self.assertEqual(project["categories"], ["utility", "management", "social"])
        self.assertEqual(project["additional_categories"], ["storage", "technology", "adventure", "game-mechanics"])

    def test_build_project_draft_can_skip_external_links(self) -> None:
        class Options:
            categories = []
            additional_categories = []
            client_side = "optional"
            server_side = "optional"
            project_status = "draft"
            version_status = "draft"
            version_type = "release"
            issues_url = "https://example.com/manual-issues"
            source_url = ""
            wiki_url = ""
            discord_url = "https://discord.gg/example"
            license_id = ""
            license_url = ""
            manifest = load_json(MANIFEST_PATH)

        metadata = {
            "loader": "forge",
            "supported_minecraft": "1.12.2",
            "resolved_range_folders": ["1.12-1.12.2"],
            "metadata": {
                "mod_id": "allmobshatethesun",
                "name": "All Mobs Hate The SUN",
                "mod_version": "1.0.0",
                "description": "Example",
                "authors": ["Itamio"],
                "license": "",
                "homepage": "https://example.com/wiki",
                "sources": "https://example.com/source",
                "issues": "https://example.com/issues",
                "entrypoint_class": "",
                "group": "",
            },
        }
        listing = {
            "name": "All Mobs Hate The SUN",
            "short_description": "Example summary",
            "long_description": "# All Mobs Hate The SUN\n\nExample body.",
        }

        project = build_project_draft(
            metadata=metadata,
            listing=listing,
            options=Options(),
            generated_links={
                "issues_url": "https://github.com/Sekai0NI0itamio/all-mobs-hate-the-sun/issues",
                "source_url": "https://github.com/Sekai0NI0itamio/all-mobs-hate-the-sun",
                "wiki_url": "https://github.com/Sekai0NI0itamio/all-mobs-hate-the-sun/wiki",
            },
            include_links=False,
        )

        self.assertNotIn("issues_url", project)
        self.assertNotIn("source_url", project)
        self.assertNotIn("wiki_url", project)
        self.assertNotIn("discord_url", project)
        self.assertNotIn("donation_urls", project)

    def test_strip_project_external_links_removes_all_external_link_fields(self) -> None:
        stripped = strip_project_external_links(
            {
                "slug": "demo",
                "title": "Demo",
                "issues_url": "https://example.com/issues",
                "source_url": "https://example.com/source",
                "wiki_url": "https://example.com/wiki",
                "discord_url": "https://discord.gg/example",
                "donation_urls": [{"id": "paypal", "platform": "PayPal", "url": "https://paypal.me/example"}],
            }
        )

        self.assertEqual(stripped["slug"], "demo")
        self.assertNotIn("issues_url", stripped)
        self.assertNotIn("source_url", stripped)
        self.assertNotIn("wiki_url", stripped)
        self.assertNotIn("discord_url", stripped)
        self.assertNotIn("donation_urls", stripped)

    def test_sync_project_description_image_prefers_managed_github_raw_url(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.get_project_calls = 0
                self.add_gallery_image_calls = 0
                self.modified_payloads: list[dict[str, str]] = []

            def get_project(self, *, project_ref: str) -> dict[str, object]:
                self.get_project_calls += 1
                return {"id": project_ref, "gallery": []}

            def add_gallery_image(self, **kwargs: object) -> None:
                self.add_gallery_image_calls += 1

            def modify_project(self, *, project_ref: str, payload: dict[str, str]) -> None:
                self.modified_payloads.append(payload)

        with tempfile.TemporaryDirectory() as temp_dir:
            bundle_dir = Path(temp_dir)
            art_dir = bundle_dir / "art"
            art_dir.mkdir(parents=True, exist_ok=True)
            Image.new("RGB", (1600, 900), (40, 80, 120)).save(art_dir / "description-image.webp")
            write_json(
                art_dir / "assets.json",
                {"description_poster_file": "art/description-image.webp"},
            )
            write_json(
                bundle_dir / "external_links.json",
                {"description_image_url": "https://raw.githubusercontent.com/Sekai0NI0itamio/demo/main/.modcompiler-art/description-image.webp"},
            )
            project_path = bundle_dir / "modrinth.project.json"
            write_json(project_path, {"title": "Demo Mod", "body": "# Demo Mod\n\nOriginal body"})
            publish_state = {
                "status": "draft_created",
                "verified": True,
                "generated_at": "2026-03-30T00:00:00Z",
                "project_id": "proj123",
                "project_slug": "demo-mod",
                "version_id": "ver123",
                "description_image_url": "",
                "description_image_urls": [],
                "last_error": "",
                "warnings": [],
            }

            client = FakeClient()
            project_payload = {"title": "Demo Mod", "body": "# Demo Mod\n\nOriginal body"}
            warnings = sync_project_description_image(
                client=client,
                project_id="proj123",
                bundle_dir=bundle_dir,
                project_payload=project_payload,
                project_path=project_path,
                publish_state=publish_state,
            )

            self.assertEqual(warnings, [])
            self.assertEqual(client.get_project_calls, 0)
            self.assertEqual(client.add_gallery_image_calls, 0)
            self.assertEqual(len(client.modified_payloads), 1)
            self.assertIn("raw.githubusercontent.com", client.modified_payloads[0]["body"])
            self.assertEqual(
                publish_state["description_image_url"],
                "https://raw.githubusercontent.com/Sekai0NI0itamio/demo/main/.modcompiler-art/description-image.webp",
            )

    def test_sync_project_description_image_ignores_external_raw_urls_when_disabled(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.get_project_calls = 0
                self.add_gallery_image_calls = 0
                self.modified_payloads: list[dict[str, str]] = []

            def get_project(self, *, project_ref: str) -> dict[str, object]:
                self.get_project_calls += 1
                if self.get_project_calls == 1:
                    return {"id": project_ref, "gallery": []}
                return {
                    "id": project_ref,
                    "gallery": [
                        {
                            "title": "Demo Mod Cover Image",
                            "url": "https://cdn.modrinth.com/data/demo/modimages/demo-cover.webp",
                        }
                    ],
                }

            def add_gallery_image(self, **kwargs: object) -> None:
                self.add_gallery_image_calls += 1

            def modify_project(self, *, project_ref: str, payload: dict[str, str]) -> None:
                self.modified_payloads.append(payload)

        with tempfile.TemporaryDirectory() as temp_dir:
            bundle_dir = Path(temp_dir)
            art_dir = bundle_dir / "art"
            art_dir.mkdir(parents=True, exist_ok=True)
            Image.new("RGB", (1600, 900), (40, 80, 120)).save(art_dir / "description-image.webp")
            write_json(
                art_dir / "assets.json",
                {"description_poster_file": "art/description-image.webp"},
            )
            write_json(
                bundle_dir / "external_links.json",
                {"description_image_url": "https://raw.githubusercontent.com/Sekai0NI0itamio/demo/main/.modcompiler-art/description-image.webp"},
            )
            project_path = bundle_dir / "modrinth.project.json"
            write_json(project_path, {"title": "Demo Mod", "body": "# Demo Mod\n\nOriginal body"})
            publish_state = {
                "status": "draft_created",
                "verified": True,
                "generated_at": "2026-03-30T00:00:00Z",
                "project_id": "proj123",
                "project_slug": "demo-mod",
                "version_id": "ver123",
                "description_image_url": "",
                "description_image_urls": [],
                "last_error": "",
                "warnings": [],
            }

            client = FakeClient()
            project_payload = {"title": "Demo Mod", "body": "# Demo Mod\n\nOriginal body"}
            warnings = sync_project_description_image(
                client=client,
                project_id="proj123",
                bundle_dir=bundle_dir,
                project_payload=project_payload,
                project_path=project_path,
                publish_state=publish_state,
                allow_external_urls=False,
            )

            self.assertEqual(warnings, [])
            self.assertEqual(client.get_project_calls, 2)
            self.assertEqual(client.add_gallery_image_calls, 1)
            self.assertEqual(len(client.modified_payloads), 1)
            self.assertIn("cdn.modrinth.com", client.modified_payloads[0]["body"])
            self.assertNotIn("raw.githubusercontent.com", client.modified_payloads[0]["body"])
            self.assertEqual(
                publish_state["description_image_url"],
                "https://cdn.modrinth.com/data/demo/modimages/demo-cover.webp",
            )

    def test_sync_project_icon_uploads_existing_bundle_icon(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.calls: list[tuple[str, str]] = []

            def change_project_icon(self, *, project_ref: str, icon_path: Path) -> None:
                self.calls.append((project_ref, icon_path.name))

        with tempfile.TemporaryDirectory() as temp_dir:
            bundle_dir = Path(temp_dir)
            Image.new("RGB", (256, 256), (30, 60, 90)).save(bundle_dir / "icon.webp")
            client = FakeClient()

            warnings = sync_project_icon(
                client=client,
                project_id="proj123",
                bundle_dir=bundle_dir,
            )

            self.assertEqual(warnings, [])
            self.assertEqual(client.calls, [("proj123", "icon.webp")])

    @mock.patch("modcompiler.auto_create_modrinth.commit_and_push_git_checkout")
    @mock.patch("modcompiler.auto_create_modrinth.clone_existing_git_repository")
    @mock.patch("modcompiler.auto_create_modrinth.configure_git_commit_identity")
    @mock.patch("modcompiler.auto_create_modrinth.initialize_git_repository")
    @mock.patch("modcompiler.auto_create_modrinth.ensure_public_github_repo")
    @mock.patch("modcompiler.auto_create_modrinth.discover_checkout_branch")
    def test_sync_bundle_github_links_preserves_repo_links_when_wiki_sync_fails(
        self,
        discover_checkout_branch: mock.Mock,
        ensure_public_github_repo: mock.Mock,
        initialize_git_repository: mock.Mock,
        configure_git_commit_identity: mock.Mock,
        clone_existing_git_repository: mock.Mock,
        commit_and_push_git_checkout: mock.Mock,
    ) -> None:
        ensure_public_github_repo.return_value = ({"default_branch": "main"}, True)
        discover_checkout_branch.return_value = "main"
        commit_and_push_git_checkout.side_effect = [True, ModCompilerError("remote wiki repository not found")]
        clone_existing_git_repository.side_effect = ModCompilerError("repository not found")

        with tempfile.TemporaryDirectory() as temp_dir:
            bundle_dir = Path(temp_dir)
            art_dir = bundle_dir / "art"
            art_dir.mkdir(parents=True, exist_ok=True)
            decompiled_dir = bundle_dir / "decompiled" / "src"
            decompiled_dir.mkdir(parents=True, exist_ok=True)
            (bundle_dir / "decompiled" / "mod_info.txt").write_text("demo", encoding="utf-8")
            Image.new("RGB", (1600, 900), (40, 80, 120)).save(art_dir / "description-image.webp")
            write_json(
                art_dir / "assets.json",
                {"description_poster_file": "art/description-image.webp"},
            )

            options = mock.Mock()
            options.github_owner = "Sekai0NI0itamio"
            options.github_token = "token"
            listing = {
                "name": "Demo Mod",
                "short_description": "Example summary",
                "long_description": "# Demo Mod\n\nExample body.",
            }
            metadata = {
                "loader": "forge",
                "supported_minecraft": "1.12.2",
                "metadata": {"mod_version": "1.0.0"},
            }

            state = sync_bundle_github_links(
                bundle_dir=bundle_dir,
                listing=listing,
                metadata=metadata,
                options=options,
            )

            self.assertEqual(state["repo_name"], "demo-mod")
            self.assertEqual(state["repo_url"], "https://github.com/Sekai0NI0itamio/demo-mod")
            self.assertEqual(state["issues_url"], "https://github.com/Sekai0NI0itamio/demo-mod/issues")
            self.assertEqual(state["source_url"], "https://github.com/Sekai0NI0itamio/demo-mod")
            self.assertEqual(state["wiki_url"], "https://github.com/Sekai0NI0itamio/demo-mod/blob/main/docs/Home.md")
            self.assertTrue(state["repo_updated"])
            self.assertFalse(state["wiki_updated"])
            self.assertIn(".modcompiler-art/description-image.webp", state["description_image_url"])
            self.assertTrue(any("GitHub wiki sync failed" in warning for warning in state["warnings"]))
            self.assertTrue(any("Falling back to repository docs page" in warning for warning in state["warnings"]))

    def test_build_generated_repo_readme_uses_minimal_credit_line(self) -> None:
        readme = build_generated_repo_readme(
            listing={
                "name": "All Mobs Hate The SUN",
                "long_description": "# All Mobs Hate The SUN\n\nHostile mobs burn in daylight.",
            },
            metadata={},
        )
        self.assertIn("# All Mobs Hate The SUN", readme)
        self.assertIn("Hostile mobs burn in daylight.", readme)
        self.assertIn("This github repository is auto created using itamio's Mod Compiler repository workflow.", readme)
        self.assertNotIn("Repository Notes", readme)
        self.assertNotIn("auto-generated by ModCompiler", readme)

    def test_build_project_create_payload_adds_initial_versions_for_api_compat(self) -> None:
        payload = {
            "slug": "demo-mod",
            "title": "Demo Mod",
            "description": "Short description",
            "categories": ["utility"],
            "client_side": "optional",
            "server_side": "required",
            "body": "Long body",
            "project_type": "mod",
        }

        create_payload = build_project_create_payload(payload)

        self.assertIn("initial_versions", create_payload)
        self.assertEqual(create_payload["initial_versions"], [])
        self.assertNotIn("initial_versions", payload)

    def test_build_project_create_payload_uses_all_rights_reserved_fallback_license(self) -> None:
        payload = {
            "slug": "demo-mod",
            "title": "Demo Mod",
            "description": "Short description",
            "categories": ["utility"],
            "client_side": "optional",
            "server_side": "required",
            "body": "Long body",
            "project_type": "mod",
        }

        create_payload = build_project_create_payload(payload)

        self.assertIn("license_id", create_payload)
        self.assertEqual(create_payload["license_id"], "LicenseRef-All-Rights-Reserved")
        self.assertNotIn("license_id", payload)

    def test_build_project_create_payload_forces_draft_project_creation(self) -> None:
        payload = {
            "slug": "demo-mod",
            "title": "Demo Mod",
            "description": "Short description",
            "categories": ["utility"],
            "client_side": "optional",
            "server_side": "required",
            "body": "Long body",
            "project_type": "mod",
            "status": "listed",
            "requested_status": "approved",
        }

        create_payload = build_project_create_payload(payload)

        self.assertEqual(create_payload["status"], "draft")
        self.assertTrue(create_payload["is_draft"])
        self.assertNotIn("requested_status", create_payload)
        self.assertEqual(payload["status"], "listed")

    def test_build_project_create_payload_uses_custom_license_ref_when_only_url_is_present(self) -> None:
        payload = {
            "slug": "demo-mod",
            "title": "Demo Mod",
            "description": "Short description",
            "categories": ["utility"],
            "client_side": "optional",
            "server_side": "required",
            "body": "Long body",
            "project_type": "mod",
            "license_url": "https://example.com/license",
        }

        create_payload = build_project_create_payload(payload)

        self.assertEqual(create_payload["license_id"], "LicenseRef-Custom")
        self.assertEqual(create_payload["license_url"], "https://example.com/license")

    def test_build_version_create_payload_preserves_empty_dependencies_and_sets_primary_file(self) -> None:
        payload = {
            "name": "Demo Mod 1.0.0",
            "version_number": "1.0.0",
            "game_versions": ["1.12.2"],
            "version_type": "release",
            "loaders": ["forge"],
            "file_parts": ["file"],
            "status": "listed",
            "requested_status": "listed",
            "client_side": "optional",
            "server_side": "required",
            "environment_group": "server_side_only",
            "environment_label": "Server-side only",
            "environment_option": "works_in_singleplayer_too",
            "environment_option_label": "Works in singleplayer too",
            "environment_summary": "All functionality is done server-side and is compatible with vanilla clients.",
            "works_in_singleplayer": True,
            "dedicated_server_only": False,
        }

        create_payload = build_version_create_payload(payload)

        self.assertIn("dependencies", create_payload)
        self.assertEqual(create_payload["dependencies"], [])
        self.assertEqual(create_payload["status"], "listed")
        self.assertEqual(create_payload["primary_file"], "file")
        self.assertNotIn("environment_group", create_payload)
        self.assertNotIn("environment_label", create_payload)
        self.assertNotIn("environment_option", create_payload)
        self.assertNotIn("works_in_singleplayer", create_payload)
        self.assertNotIn("dependencies", payload)
        self.assertEqual(payload["status"], "listed")

    def test_resolve_description_image_files_for_upload_prefers_full_poster(self) -> None:
        files = resolve_description_image_files_for_upload(
            {
                "description_poster_file": "art/description-image.webp",
                "description_image_files": ["art/description-image-panel-01.webp", "art/description-image-panel-02.webp"],
                "description_image_file": "art/description-image-panel-01.webp",
            }
        )
        self.assertEqual(files, ["art/description-image.webp"])

    def test_resolve_modrinth_project_identity_falls_back_to_slug_when_saved_id_is_stale(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.calls: list[str] = []

            def get_project(self, *, project_ref: str) -> dict[str, str]:
                self.calls.append(project_ref)
                if project_ref == "bad-id":
                    raise ModCompilerError("The Modrinth project could not be found.")
                if project_ref == "all-mobs-hate-the-sun":
                    return {"id": "good-id", "slug": "all-mobs-hate-the-sun"}
                raise ModCompilerError(f"Unexpected ref {project_ref}")

        client = FakeClient()

        project_id, project_slug = resolve_modrinth_project_identity(
            client=client,
            project_id="bad-id",
            project_slug="all-mobs-hate-the-sun",
        )

        self.assertEqual(project_id, "good-id")
        self.assertEqual(project_slug, "all-mobs-hate-the-sun")
        self.assertEqual(client.calls, ["bad-id", "all-mobs-hate-the-sun"])

    def test_wait_for_modrinth_project_ready_retries_until_project_resolves(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.calls: list[str] = []
                self.slug_calls = 0

            def get_project(self, *, project_ref: str) -> dict[str, str]:
                self.calls.append(project_ref)
                if project_ref == "proj123":
                    raise ModCompilerError("The Modrinth project could not be found.")
                if project_ref == "day-counter":
                    self.slug_calls += 1
                    if self.slug_calls < 3:
                        raise ModCompilerError("The Modrinth project could not be found.")
                    return {"id": "proj123", "slug": "day-counter"}
                raise ModCompilerError(f"Unexpected ref {project_ref}")

        client = FakeClient()

        with mock.patch("modcompiler.auto_create_modrinth.time.sleep") as sleep_mock:
            project_id, project_slug = wait_for_modrinth_project_ready(
                client=client,
                project_id="proj123",
                project_slug="day-counter",
                max_attempts=4,
            )

        self.assertEqual(project_id, "proj123")
        self.assertEqual(project_slug, "day-counter")
        self.assertEqual(sleep_mock.call_count, 2)

    def test_create_modrinth_version_after_project_ready_retries_fresh_project_upload_once(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.create_calls = 0
                self.get_calls: list[str] = []
                self.payloads: list[dict[str, object]] = []

            def create_version(self, *, payload: dict[str, object], jar_path: Path) -> dict[str, str]:
                self.create_calls += 1
                self.payloads.append(dict(payload))
                if self.create_calls == 1:
                    raise ModCompilerError(
                        "Modrinth authentication failed. Response: Authentication Error: You don't have permission to upload this version!"
                    )
                return {"id": "ver123"}

            def get_project(self, *, project_ref: str) -> dict[str, str]:
                self.get_calls.append(project_ref)
                return {"id": "proj123", "slug": "day-counter"}

        client = FakeClient()

        with tempfile.TemporaryDirectory() as temp_dir:
            jar_path = Path(temp_dir) / "Day-Counter-1.0.0.jar"
            jar_path.write_bytes(b"jar")

            with mock.patch("modcompiler.auto_create_modrinth.time.sleep") as sleep_mock:
                created = create_modrinth_version_after_project_ready(
                    client=client,
                    version_payload={
                        "project_id": "proj123",
                        "version_number": "1.0.0",
                        "loaders": ["forge"],
                        "game_versions": ["1.12.2"],
                        "status": "listed",
                        "client_side": "required",
                        "server_side": "optional",
                        "unsupported_extra_field": "remove-me-on-retry",
                    },
                    jar_path=jar_path,
                    project_id="proj123",
                    project_slug="day-counter",
                    fresh_project=True,
                )

        self.assertEqual(created["id"], "ver123")
        self.assertEqual(client.create_calls, 2)
        self.assertGreaterEqual(len(client.get_calls), 1)
        self.assertGreaterEqual(sleep_mock.call_count, 1)
        self.assertEqual(client.payloads[0]["unsupported_extra_field"], "remove-me-on-retry")
        self.assertNotIn("unsupported_extra_field", client.payloads[1])
        self.assertNotIn("client_side", client.payloads[1])
        self.assertNotIn("server_side", client.payloads[1])

    def test_choose_available_modrinth_project_identity_uses_friendly_alternatives_for_taken_slug(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.calls: list[str] = []

            def get_project(self, *, project_ref: str) -> dict[str, object]:
                self.calls.append(project_ref)
                if project_ref in {"day-counter", "time-counter"}:
                    return {"id": f"id-{project_ref}", "slug": project_ref}
                if project_ref == "day-and-hour-counter":
                    raise ModCompilerError("The Modrinth project could not be found.")
                raise ModCompilerError(f"Unexpected ref {project_ref}")

        client = FakeClient()

        title, slug, warning = choose_available_modrinth_project_identity(
            client=client,
            project_payload={
                "title": "Day Counter",
                "slug": "day-counter",
                "description": "Shows the current Minecraft day plus the hour on screen.",
                "body": "# Day Counter\n\nShows the current Minecraft day plus hour and minute on screen.",
            },
            version_payload={"loaders": ["forge"], "game_versions": ["1.12.2"]},
        )

        self.assertEqual(title, "Day and Hour Counter")
        self.assertEqual(slug, "day-and-hour-counter")
        self.assertIn("day-counter", warning)
        self.assertEqual(client.calls, ["day-counter", "time-counter", "day-and-hour-counter"])

    def test_publish_bundle_ignores_unconfirmed_saved_project_id_and_renames_taken_slug(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.calls: list[str] = []

            def get_project(self, *, project_ref: str) -> dict[str, object]:
                self.calls.append(project_ref)
                if project_ref == "day-counter":
                    return {
                        "id": "DAhFhwDG",
                        "slug": "day-counter",
                        "title": "Day Counter",
                        "project_type": "project",
                        "loaders": ["paper"],
                    }
                if project_ref == "time-counter":
                    raise ModCompilerError("The Modrinth project could not be found.")
                raise ModCompilerError(f"Unexpected ref {project_ref}")

        with tempfile.TemporaryDirectory() as temp_dir:
            bundle_dir = Path(temp_dir) / "day-counter-1.0.0"
            input_dir = bundle_dir / "input"
            input_dir.mkdir(parents=True)
            (input_dir / "Day-Counter-1.0.0.jar").write_bytes(b"jar")

            write_json(
                bundle_dir / "bundle_metadata.json",
                {
                    "bundle_slug": "day-counter-1.0.0",
                    "jar_name": "Day-Counter-1.0.0.jar",
                    "loader": "forge",
                    "resolved_game_versions": ["1.12.2"],
                },
            )
            write_json(
                bundle_dir / "listing.json",
                {
                    "name": "Day Counter",
                    "summary": "Shows the current Minecraft day plus real-world hour and minute on screen.",
                    "long_description": "# Day Counter\n\nShows the current Minecraft day plus hour and minute on screen.",
                },
            )
            write_json(
                bundle_dir / "modrinth.project.json",
                {
                    "slug": "day-counter",
                    "title": "Day Counter",
                    "description": "Shows the current Minecraft day plus real-world hour and minute on screen.",
                    "categories": ["utility"],
                    "client_side": "required",
                    "server_side": "optional",
                    "body": "# Day Counter\n\nShows the current Minecraft day plus hour and minute on screen.",
                    "project_type": "mod",
                },
            )
            write_json(
                bundle_dir / "modrinth.version.json",
                {
                    "name": "Day Counter 1.0.0",
                    "version_number": "1.0.0",
                    "game_versions": ["1.12.2"],
                    "version_type": "release",
                    "loaders": ["forge"],
                    "file_parts": ["file"],
                },
            )
            (bundle_dir / "verify.txt").write_text("verified\n", encoding="utf-8")
            write_json(
                bundle_dir / "draft_state.json",
                {
                    "status": "draft_create_failed",
                    "verified": True,
                    "project_id": "DAhFhwDG",
                    "project_slug": "day-counter",
                    "version_id": "",
                    "warnings": [],
                },
            )

            result = publish_bundle(
                bundle_dir=bundle_dir,
                client=FakeClient(),
                dry_run=True,
                assume_verified=False,
                disable_links=False,
            )

            self.assertEqual(result["status"], "dry_run")
            self.assertEqual(result["project_id"], "")
            self.assertEqual(result["project_slug"], "time-counter")
            self.assertEqual(result["project_url"], "https://modrinth.com/mod/time-counter")

            project_payload = load_json(bundle_dir / "modrinth.project.json")
            self.assertEqual(project_payload["title"], "Time Counter")
            self.assertEqual(project_payload["slug"], "time-counter")
            self.assertTrue(project_payload["body"].startswith("# Time Counter"))

            listing = load_json(bundle_dir / "listing.json")
            self.assertEqual(listing["name"], "Time Counter")
            self.assertTrue(listing["long_description"].startswith("# Time Counter"))

            draft_state = load_json(bundle_dir / "draft_state.json")
            self.assertEqual(draft_state["project_id"], "")
            self.assertEqual(draft_state["project_slug"], "time-counter")
            self.assertFalse(draft_state.get("project_created_by_modcompiler"))

    def test_promote_published_bundle_visibility_updates_version_status_when_not_draft(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.version_calls: list[tuple[str, dict[str, str]]] = []
                self.project_calls: list[tuple[str, dict[str, str]]] = []

            def modify_version(self, *, version_id: str, payload: dict[str, str]) -> None:
                self.version_calls.append((version_id, payload))

            def modify_project(self, *, project_ref: str, payload: dict[str, str]) -> None:
                self.project_calls.append((project_ref, payload))

        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            project_path = root / "modrinth.project.json"
            version_path = root / "modrinth.version.json"
            project_path.write_text("{}", encoding="utf-8")
            version_path.write_text("{}", encoding="utf-8")
            project_payload = {"status": "draft"}
            version_payload = {"status": "listed"}
            client = FakeClient()

            project_target, version_target = promote_published_bundle_visibility(
                client=client,
                project_id="proj123",
                version_id="ver123",
                project_payload=project_payload,
                version_payload=version_payload,
                project_path=project_path,
                version_path=version_path,
            )

            self.assertEqual(project_target, "draft")
            self.assertEqual(version_target, "listed")
            self.assertEqual(client.version_calls, [("ver123", {"status": "listed"})])
            self.assertEqual(client.project_calls, [])
            self.assertEqual(load_json(version_path)["status"], "listed")

    def test_default_categories_for_metadata_uses_keyword_inference(self) -> None:
        categories = default_categories_for_metadata(
            {
                "metadata": {
                    "name": "Instant Hoppers",
                    "description": "Improves hopper item transport and storage automation.",
                    "mod_id": "instant_hoppers",
                }
            }
        )
        self.assertEqual(categories, ["storage", "technology", "transportation"])

    def test_finalize_category_selection_promotes_additional_when_primary_is_empty(self) -> None:
        primary, additional = finalize_category_selection(
            primary_candidates=[],
            additional_candidates=["storage", "technology", "utility", "management"],
            metadata={"metadata": {"name": "Demo Mod", "description": "Example"}},
        )
        self.assertEqual(primary, ["storage", "technology", "utility"])
        self.assertEqual(additional, ["management"])

    def test_build_modrinth_category_guidance_lists_current_official_mod_categories(self) -> None:
        guidance = build_modrinth_category_guidance()
        self.assertIn("- adventure:", guidance)
        self.assertIn("- worldgen:", guidance)
        self.assertIn("- storage:", guidance)

    def test_build_modrinth_urls_prefer_slug_and_include_version(self) -> None:
        self.assertEqual(
            build_modrinth_project_url(project_slug="all-most-hate-the-sun", project_id="n6Z9u85m"),
            "https://modrinth.com/mod/all-most-hate-the-sun",
        )
        self.assertEqual(
            build_modrinth_version_url(
                project_slug="all-most-hate-the-sun",
                project_id="n6Z9u85m",
                version_id="muFa3Uev",
            ),
            "https://modrinth.com/mod/all-most-hate-the-sun/version/muFa3Uev",
        )

    def test_remote_publish_runtime_paths_include_modrinth_runtime_dependencies(self) -> None:
        runtime_paths = {str(path) for path in remote_publish_runtime_paths()}

        self.assertIn("scripts/auto_create_modrinth_draft_projects.py", runtime_paths)
        self.assertIn("modcompiler/auto_create_modrinth.py", runtime_paths)
        self.assertIn("modcompiler/modrinth.py", runtime_paths)
        self.assertIn("modcompiler/common.py", runtime_paths)
        self.assertIn("modcompiler/decompile.py", runtime_paths)

    def test_parse_github_repo_from_remote_handles_https_and_ssh(self) -> None:
        self.assertEqual(
            parse_github_repo_from_remote("https://github.com/Sekai0NI0itamio/ModCompiler.git"),
            "Sekai0NI0itamio/ModCompiler",
        )
        self.assertEqual(
            parse_github_repo_from_remote("git@github.com:Sekai0NI0itamio/ModCompiler.git"),
            "Sekai0NI0itamio/ModCompiler",
        )

    def test_resolve_decompiled_source_roots_handles_remote_artifact_layout(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            source_file = root / "src" / "main" / "java" / "com" / "example" / "DemoMod.java"
            source_file.parent.mkdir(parents=True, exist_ok=True)
            source_file.write_text("package com.example;\nclass DemoMod {}\n", encoding="utf-8")

            roots = resolve_decompiled_source_roots(root)
            self.assertEqual(roots, [root / "src" / "main" / "java"])
            self.assertEqual(source_path_label(source_file, roots), "com/example/DemoMod.java")

    def test_normalize_publish_via_accepts_auto_local_and_github(self) -> None:
        self.assertEqual(normalize_publish_via("auto"), "auto")
        self.assertEqual(normalize_publish_via("local"), "local")
        self.assertEqual(normalize_publish_via("github"), "github")


if __name__ == "__main__":
    unittest.main()
