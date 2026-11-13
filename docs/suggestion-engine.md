# Suggestion engine

How the suggestion strip picks its three words.

## While typing a word (completion / correction)

1. **Lexicon** – `VocabularyIndex` holds ~30K words with a usage score (`en-US.v2.vocab`, built once from the
   phrase pack's stored frequencies). Completions of a prefix are ranked by `ln(1 + score)` plus a bonus for the
   curated list in `Words`, minus a small length penalty. Top results per prefix are cached.
2. **Candidate pool** – `Words.suggestPool` merges learned words (+ priority), lexicon completions and the curated
   list. `UserLanguageModel.rankPrefix` widens it with the words the n-gram model expects after the previous
   words (`NextWordPredictor.predictForPrefix`) and the user's own bigrams/trigrams, then scores everything:
   user history + n-gram context + lexicon frequency.
3. **Downloaded packs** – `DownloadedVocabularyIndex.suggest` no longer returns alphabetical order: English packs
   reuse the lexicon scores, other languages prefer shorter completions.
4. **Auto-correct** – `Words.correction` applies, in order: never touch learned words; a table of near-certain fixes
   (`dont` -> `don't`, lone `i` -> `I`, `teh` -> `the`, ...); never touch real lexicon words; otherwise one close
   candidate, or the most frequent of several if it clearly dominates the runner-up.

## Between words (next-word prediction)

`NextWordPredictor` blends the 3-, 2- and 1-word contexts using the stored frequencies (weights 1.0 / 0.4 / 0.16)
instead of taking the first words found. A new sentence resets the context, and after a sentence end the strip
shows sentence starters. Once the model is loaded it never returns an empty list, so the keyboard does not keep
re-polling it for unseen contexts.

## Learning

`UserLanguageModel` keeps unigram/bigram/trigram counts (max 4000 keys). When full it drops the oldest rarely-seen
entries first; the old policy dropped the lowest counts, i.e. whatever was just learned.
