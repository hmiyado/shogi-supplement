# Isolated worker image check

This builds a runnable candidate from an immutable baseline worker image and the locally built worker distribution. It preserves the baseline engine, adds the study engine, and uses the normal worker entrypoint. The test services and credentials are not included in the candidate image.

Prepare a new context directory containing:

- `worker/`: output of `:server:worker:installDist`
- `study-engine`: Linux x86_64 AVX2 binary matching the checksum in `Dockerfile.candidate`
- `study-nn.bin`: Aoba evaluation matching the checksum in `Dockerfile.candidate`
- `study-LICENSE`: accompanying engine license
- `checks/`: a copy of this directory
- `manifest.json`: relative paths, sizes, and SHA-256 of the payload files

Use a dedicated candidate image name/tag. Never pass a production tag or `latest` as `_IMAGE`.

```sh
gcloud builds submit /path/to/context \
  --config=tools/worker-image-check/cloudbuild.yaml \
  --substitutions=_BASE_IMAGE=REGISTRY/BASE@sha256:DIGEST,_IMAGE=REGISTRY/STUDY:verify-UNIQUE \
  --timeout=600s --async
```

The build uploads the prepared context, consumes Cloud Build/storage quota, and pushes only the candidate image if all checks pass. It does not deploy Cloud Run or change production service configuration. Retain the resulting registry digest for any separately authorized deployment.

The worker runs with a 4-CPU quota and 2GiB memory limit, on an internal Docker network without outbound connectivity. An isolated fixture supplies freshly generated RSA JWT/JWKS and a small in-memory PostgREST-compatible store. The worker uses its real authentication verifier, HTTP server, repository implementation, subprocess engines, and NDJSON response path. Firebase App Check is disabled only through the test container's environment. The worker image contains no test authentication bypass.

Checks cover invalid JWT rejection; study/legacy/drill profile separation; three study PVs and their legality; persistence of metadata; cache reuse; engine stop/resume; and memory/OOM state. The fixture does not reproduce PostgreSQL constraints, RLS, concurrent transactions, or the actual Supabase service. Resource limits do not guarantee performance on Cloud Run's CPU model.

`engine-stop.sh` requires Linux Bash 4+ (`coproc`); macOS's system Bash 3 cannot syntax-check it. The Cloud Build test uses the candidate's Linux Bash.

The build host uses `E2_HIGHCPU_8` to accommodate the 4-CPU worker and 1-CPU fixture. The default 2-CPU build host rejects the worker CPU limit before startup.
