alter table summoner drop column if exists seed_status;
alter table summoner drop column if exists last_seed_run_at;
alter table summoner drop column if exists expand_status;
alter table summoner drop column if exists last_expand_run_at;
alter table summoner drop column if exists refresh_status;
alter table summoner drop column if exists last_refresh_run_at;
