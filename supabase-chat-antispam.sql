-- Антиспам: не чаще одного сообщения за 10 секунд на аккаунт.
-- Выполняется после supabase-chat.sql. История сообщений сохраняется.
begin;

create index if not exists chat_messages_author_created_idx
    on public.chat_messages (author_id, created_at desc);

-- Запретить обход ограничения прямым запросом к таблице.
revoke insert on public.chat_messages from public, anon, authenticated;
revoke insert (id, author_id, body, created_at) on public.chat_messages from public, anon, authenticated;

create or replace function public.send_chat_message(message_body text)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
    sender uuid := auth.uid();
    previous timestamptz;
    remaining integer;
    trimmed_message text := btrim(message_body);
begin
    if sender is null then
        raise exception 'Требуется вход в аккаунт' using errcode = '42501';
    end if;
    if trimmed_message is null or char_length(trimmed_message) not between 1 and 500 then
        raise exception 'Сообщение должно содержать от 1 до 500 символов' using errcode = '22023';
    end if;
    -- Блокировка делает проверку и отправку одного пользователя последовательными.
    perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended('skydive-chat:' || sender::text, 0));
    select m.created_at into previous from public.chat_messages m
        where m.author_id = sender order by m.created_at desc limit 1;
    if previous is not null and previous + interval '10 seconds' > clock_timestamp() then
        remaining := greatest(1, ceil(extract(epoch from (previous + interval '10 seconds' - clock_timestamp())))::integer);
        return jsonb_build_object('ok', false, 'wait_seconds', remaining);
    end if;
    insert into public.chat_messages(author_id, body, created_at)
        values (sender, trimmed_message, clock_timestamp());
    return jsonb_build_object('ok', true);
end;
$$;
revoke all on function public.send_chat_message(text) from public, anon, authenticated;
grant execute on function public.send_chat_message(text) to authenticated;

commit;
