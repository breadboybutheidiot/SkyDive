-- Запустите один раз в Supabase SQL Editor. Аккаунты уже хранятся в auth.users.
create table if not exists public.jumps (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
    jump_date text not null,
    location text not null,
    altitude text not null default '',
    discipline text not null default '',
    notes text not null default '',
    created_at timestamptz not null default now()
);
create index if not exists jumps_user_created_idx on public.jumps (user_id, created_at desc);
alter table public.jumps enable row level security;
revoke all on public.jumps from anon, authenticated;
grant select, insert, delete on public.jumps to authenticated;
drop policy if exists "Users can read their jumps" on public.jumps;
create policy "Users can read their jumps" on public.jumps for select to authenticated using ((select auth.uid()) = user_id);
drop policy if exists "Users can create their jumps" on public.jumps;
create policy "Users can create their jumps" on public.jumps for insert to authenticated with check ((select auth.uid()) = user_id);
drop policy if exists "Users can delete their jumps" on public.jumps;
create policy "Users can delete their jumps" on public.jumps for delete to authenticated using ((select auth.uid()) = user_id);
