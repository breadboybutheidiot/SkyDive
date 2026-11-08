-- Выполни в SQL Editor проекта Supabase.
create table if not exists public.skydiver_profiles (
    user_id uuid primary key default auth.uid() references auth.users(id) on delete cascade,
    display_name text not null check (char_length(trim(display_name)) between 1 and 40),
    photo_base64 text check (photo_base64 is null or char_length(photo_base64) <= 200000)
);
alter table public.skydiver_profiles enable row level security;
revoke all on public.skydiver_profiles from public, anon, authenticated;
grant select, insert, update on public.skydiver_profiles to authenticated;
drop policy if exists "Read own profile" on public.skydiver_profiles;
create policy "Read own profile" on public.skydiver_profiles for select to authenticated using ((select auth.uid()) = user_id);
drop policy if exists "Create own profile" on public.skydiver_profiles;
create policy "Create own profile" on public.skydiver_profiles for insert to authenticated with check ((select auth.uid()) = user_id);
drop policy if exists "Update own profile" on public.skydiver_profiles;
create policy "Update own profile" on public.skydiver_profiles for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
