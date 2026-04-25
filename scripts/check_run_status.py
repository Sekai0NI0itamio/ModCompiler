import json, sys

path = sys.argv[1] if len(sys.argv) > 1 else '/tmp/run_status.json'
with open(path) as f:
    d = json.load(f)

status = d.get('status')
conclusion = d.get('conclusion') or '(running)'
jobs = d.get('jobs', [])
total = len(jobs)
done = sum(1 for j in jobs if j.get('status') == 'completed')
running = sum(1 for j in jobs if j.get('status') == 'in_progress')
queued = sum(1 for j in jobs if j.get('status') == 'queued')
failed = [j for j in jobs if j.get('conclusion') in ('failure', 'cancelled')]

print(f'Status: {status} | Conclusion: {conclusion}')
print(f'Progress: {done}/{total} completed, {running} running, {queued} queued')

if failed:
    print(f'FAILURES ({len(failed)}):')
    for j in failed:
        print(f'  - {j["name"]}')
else:
    print('No failures.')
