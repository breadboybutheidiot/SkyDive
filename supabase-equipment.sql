-- Создаёт персональный чек-лист. Выполнить в Supabase SQL Editor.
create table if not exists public.equipment_checklists (
    user_id uuid primary key default auth.uid() references auth.users(id) on delete cascade,
    checked jsonb not null default '[]'::jsonb check (jsonb_typeof(checked) = 'array')
);
alter table public.equipment_checklists enable row level security;
revoke all on public.equipment_checklists from public, anon, authenticated;
grant select, insert, update on public.equipment_checklists to authenticated;
drop policy if exists "Read own checklist" on public.equipment_checklists;
create policy "Read own checklist" on public.equipment_checklists for select to authenticated using ((select auth.uid()) = user_id);
drop policy if exists "Create own checklist" on public.equipment_checklists;
create policy "Create own checklist" on public.equipment_checklists for insert to authenticated with check ((select auth.uid()) = user_id);
drop policy if exists "Update own checklist" on public.equipment_checklists;
create policy "Update own checklist" on public.equipment_checklists for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
