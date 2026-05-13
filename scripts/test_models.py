"""
test_models.py — NVIDIA model tester and benchmarker.

Usage:
  python3 scripts/test_models.py                    # List and quick-test all models
  python3 scripts/test_models.py --benchmark         # Full benchmark (slower)
  python3 scripts/test_models.py --benchmark --count 5   # Benchmark top 5
  python3 scripts/test_models.py --model nvidia/llama-3.1-nemotron-70b-instruct  # Test specific model
"""

from __future__ import annotations

import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from _ai_client import list_models, test_model_basic, benchmark_model, ResponseTooLarge
from _key_manager import KeyManager

# Import provider config from main script
from run_mod_to_all_converter import _get_ai_config, _apply_ai_config


def _load_key(intelligent: bool = False) -> str:
    cfg = _get_ai_config(intelligent)
    km = KeyManager(cfg["key_file"], env_vars=cfg["key_env_vars"])
    return km.acquire()


def _base_url(intelligent: bool = False) -> str:
    _apply_ai_config(intelligent)
    from run_mod_to_all_converter import AI_NVIDIA_BASE
    return AI_NVIDIA_BASE


def cmd_list_and_quick_test(api_key: str, base_url: str) -> None:
    """List all models and quick-test each."""
    print("Fetching model list from NVIDIA...")
    models = list_models(api_key, base_url)
    print(f"Found {len(models)} models\n")

    # Filter to likely useful models (chat-oriented, not embeddings/safety)
    chat_models = [
        m for m in models
        if not any(x in m["id"].lower() for x in ["embed", "safety", "guard", "reward", "parse", "content"])
    ]
    print(f"Testing {len(chat_models)} chat-capable models...\n")

    results = []
    for i, m in enumerate(chat_models):
        mid = m["id"]
        print(f"  [{i+1}/{len(chat_models)}] {mid}...", end=" ", flush=True)
        result = test_model_basic(mid, base_url, api_key)
        results.append(result)
        if result["status"] == "ok":
            print(f"OK ({result['time']}s)")
        else:
            print(f"FAILED ({result.get('error', '?')})")

    # Summary
    working = [r for r in results if r["status"] == "ok"]
    failed = [r for r in results if r["status"] != "ok"]
    print(f"\n{'='*60}")
    print(f"Results: {len(working)} working, {len(failed)} failed")
    if working:
        print(f"\nWorking models (sorted by speed):")
        for r in sorted(working, key=lambda x: x["time"]):
            print(f"  {r['time']:5.2f}s  {r['model']}")
    if failed:
        print(f"\nFailed models:")
        for r in failed:
            print(f"  {r['model']}: {r.get('error', '?')}")


def cmd_benchmark(api_key: str, base_url: str, count: int = 0, specific: str = "") -> None:
    """Benchmark working models on a coding task."""
    models = list_models(api_key, base_url)

    # Filter chat models
    chat_models = [
        m["id"] for m in models
        if not any(x in m["id"].lower() for x in ["embed", "safety", "guard", "reward", "parse", "content"])
    ]

    if specific:
        if specific in chat_models:
            chat_models = [specific]
        else:
            print(f"Model '{specific}' not found!")
            return

    # Quick-test first to filter working models
    print("Quick-testing models...")
    working = []
    for mid in chat_models:
        print(f"  {mid}...", end=" ", flush=True)
        r = test_model_basic(mid, base_url, api_key)
        if r["status"] == "ok":
            working.append(mid)
            print(f"OK")
        else:
            print(f"SKIP ({r.get('error', '?')[:40]})")

    if not working:
        print("\nNo working models found!")
        return

    if count > 0:
        working = working[:count]

    print(f"\nBenchmarking {len(working)} models on coding task...")
    print(f"{'Model':<55} {'Status':<10} {'Time':<8} {'Chars':<8} {'Reason':<6} {'Acc':<6}")
    print("-" * 100)

    results = []
    for mid in working:
        r = benchmark_model(mid, base_url, api_key,
                            prompt="Write a Java class with a method that adds two numbers. Just the code.",
                            expected_keywords=["class", "public", "int", "return"])
        results.append(r)
        status = r["status"]
        if status == "ok":
            print(f"  {mid:<53} {status:<10} {r['time']:<8} {r['chars']:<8} {r.get('reasoning_chunks',0):<6} {r['accuracy']:<6}")
        else:
            err = r.get("error", r["status"])[:30]
            print(f"  {mid:<53} {status:<10} {r['time']:<8} {err:<20}")

    # Summary
    passed = [r for r in results if r["status"] == "ok"]
    if passed:
        print(f"\n{'='*60}")
        print(f"Top models by accuracy + speed:")
        passed.sort(key=lambda x: (-x["accuracy"], x["time"]))
        for r in passed[:5]:
            print(f"  {r['accuracy']:.0%} acc, {r['time']:5.2f}s, {r['chars']} chars  {r['model']}")


def main() -> int:
    import argparse
    parser = argparse.ArgumentParser(description="NVIDIA model tester and benchmarker")
    parser.add_argument("--benchmark", action="store_true", help="Run full benchmarks")
    parser.add_argument("--count", type=int, default=0, help="Limit number of models to benchmark")
    parser.add_argument("--intelligent", action="store_true",
        help="Use DeepSeek instead of NVIDIA")
    parser.add_argument("--model", default="", help="Test/benchmark a specific model")
    args = parser.parse_args()

    api_key = _load_key(args.intelligent)
    base_url = _base_url(args.intelligent)

    if args.benchmark:
        cmd_benchmark(api_key, base_url, count=args.count, specific=args.model)
    else:
        if args.model:
            # Quick-test specific model
            result = test_model_basic(args.model, base_url, api_key)
            if result["status"] == "ok":
                print(f"{result['model']}: OK ({result['time']}s)")
                print(f"Response: {result['response']}")
            else:
                print(f"{result['model']}: FAILED ({result.get('error', '?')})")
        else:
            cmd_list_and_quick_test(api_key, base_url)

    return 0


if __name__ == "__main__":
    sys.exit(main())
