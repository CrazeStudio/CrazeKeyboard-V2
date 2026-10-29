-- Local on-device schema for the keyboard clone (SQLite, accessed through Room).
-- Deviations from the web template, on purpose:
--   * ids are integer rowids or natural text keys, not uuids (single user, one device)
--   * times are integer epoch milliseconds in UTC (SQLite has no timestamptz)
--   * no owner column: the database is app-private and there is exactly one user
-- Scalar preferences (height, haptics, theme id, ...) stay in SharedPreferences.
PRAGMA foreign_keys = ON;

create table languages (
  id              text primary key,                    -- BCP-47, e.g. 'en-US', 'bn-BD'
  name            text not null,
  layout_id       text not null,                       -- file name under assets/layouts/
  position        integer not null,
  enabled         integer not null default 1 check (enabled in (0, 1)),
  glide_supported integer not null default 0 check (glide_supported in (0, 1)),
  created_at      integer not null default (strftime('%s','now') * 1000)
);
create unique index uq_languages_position on languages (position);

create table learned_words (
  id           integer primary key,
  language_id  text not null references languages(id) on delete cascade,
  word         text not null,                          -- as typed
  word_norm    text not null,                          -- lower-cased, NFC, for lookup
  frequency    integer not null default 1 check (frequency > 0),
  last_used_at integer not null,
  created_at   integer not null default (strftime('%s','now') * 1000),
  unique (language_id, word)
);
create index idx_learned_prefix on learned_words (language_id, word_norm);
create index idx_learned_freq   on learned_words (language_id, frequency desc);

create table user_bigrams (
  language_id  text not null references languages(id) on delete cascade,
  prev_norm    text not null,
  word_norm    text not null,
  count        integer not null default 1 check (count > 0),
  last_used_at integer not null,
  primary key (language_id, prev_norm, word_norm)
) without rowid;
create index idx_bigrams_rank on user_bigrams (language_id, prev_norm, count desc);

create table shortcuts (
  id          integer primary key,
  language_id text references languages(id) on delete cascade,   -- null = all languages
  trigger     text not null check (length(trigger) > 0),
  expansion   text not null check (length(expansion) > 0),
  created_at  integer not null default (strftime('%s','now') * 1000)
);
create unique index uq_shortcuts_trigger on shortcuts (coalesce(language_id, ''), trigger);

create table clipboard_items (
  id           integer primary key,
  kind         text not null check (kind in ('text', 'image')),
  text_content text,
  file_path    text,                                   -- app-private, excluded from backup
  mime_type    text,
  pinned       integer not null default 0 check (pinned in (0, 1)),
  created_at   integer not null,
  expires_at   integer,                                -- null for pinned items
  check (
    (kind = 'text'  and text_content is not null and file_path is null) or
    (kind = 'image' and file_path is not null and text_content is null)
  ),
  check (pinned = 0 or expires_at is null)
);
create index idx_clip_list   on clipboard_items (pinned desc, created_at desc);
create index idx_clip_expiry on clipboard_items (expires_at) where expires_at is not null;

-- Keep at most 50 unpinned clips. Image files of deleted rows are removed in code.
create trigger trg_clipboard_cap after insert on clipboard_items
when new.pinned = 0
begin
  delete from clipboard_items
  where pinned = 0
    and id not in (
      select id from clipboard_items where pinned = 0
      order by created_at desc, id desc limit 50
    );
end;

create table recent_emoji (
  emoji        text primary key,
  use_count    integer not null default 1 check (use_count > 0),
  last_used_at integer not null
);
create index idx_recent_emoji on recent_emoji (last_used_at desc);

create table toolbar_items (
  item_id  text primary key,                           -- 'clipboard', 'editing', 'voice', ...
  position integer not null unique,
  visible  integer not null default 1 check (visible in (0, 1))
);

create table themes (
  id              text primary key,
  name            text not null,
  is_builtin      integer not null default 0 check (is_builtin in (0, 1)),
  colors_json     text not null,                       -- validated in code
  background_path text,
  key_border      integer not null default 0 check (key_border in (0, 1)),
  created_at      integer not null default (strftime('%s','now') * 1000)
);

create table voice_models (
  language_id  text primary key references languages(id) on delete cascade,
  path         text not null,
  size_bytes   integer not null check (size_bytes > 0),
  version      text not null,
  source       text not null check (source in ('bundled', 'imported')),
  installed_at integer not null default (strftime('%s','now') * 1000)
);
