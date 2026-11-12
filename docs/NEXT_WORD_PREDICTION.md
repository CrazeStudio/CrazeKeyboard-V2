# CrazeKeyboard English next-word prediction

CrazeKeyboard uses a local word-level n-gram model for the suggestion strip. It is not an LLM and performs no network requests.

## Runtime design

- Context order: trigram, then bigram, then unigram.
- The model is a memory-mapped binary file; it is not expanded into a Kotlin list or hash map.
- The lookup index is binary-searched, and candidate strings are allocated only for the three displayed suggestions.
- Model location: `filesDir/language_models/en-US.cwp`.
- The current keyboard falls back to its existing local dictionary/starter suggestions when the model pack is absent.
- Password, email, URI, no-suggestions and no-personalized-learning fields retain the existing suppression rules.

## Pack format

The `.cwp` file uses a little-endian CWP1 header, version, count, sorted (FNV-1a hash, absolute record offset) index, and variable-length context/candidate records. The context string is stored in every record to verify hash collisions.

## Build a pack

Use a properly licensed English corpus (sentence-per-line UTF-8 text):

```sh
python3 tools/build_next_word_model.py corpus.txt app/src/main/assets/en-US.cwp
```

The build script prints the resulting size. Use a corpus and pruning settings that produce a 20–30 MiB pack. Do not bundle corpora whose redistribution terms do not permit it; keep the corpus source, license and attribution alongside the generated pack.

## Memory target

The runtime maps the pack read-only and keeps no in-memory copy of the full n-gram table. This avoids a 20–30 MiB heap allocation, but it does not mean zero RAM: the OS may page mapped data into memory as accessed. Validate actual process PSS on the target phone before claiming a sub-5 MiB total-RAM guarantee.


## CrazePredictionAPI vocabulary packs

- Open Settings → Language packs and download English, Bengali or Hindi.
- The keyboard fetches the compressed `.cpack` from CrazePredictionAPI, verifies its SHA-256 against the manifest, and writes a sorted local word index.
- The sorted index is memory-mapped and used for offline prefix suggestions; the original archive is retained for future update checks.
- Online prefix suggestions are separately controlled by the **Online prefix suggestions** switch. Only the current prefix and language are sent to `/v1/predict`; no surrounding sentence is transmitted.
- API base URL: `https://crazepredictionapi.onrender.com`.
