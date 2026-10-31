create table conversations (
    id uuid primary key,
    title varchar(120) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table messages (
    id uuid primary key,
    conversation_id uuid not null references conversations(id) on delete cascade,
    role varchar(20) not null check (role in ('USER', 'ASSISTANT')),
    content text not null,
    created_at timestamp with time zone not null
);

create index idx_conversations_updated_at on conversations(updated_at desc);
create index idx_messages_conversation_created on messages(conversation_id, created_at);
