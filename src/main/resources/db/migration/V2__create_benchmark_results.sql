create table benchmark_results (
    id uuid primary key,
    model varchar(100) not null,
    prompt_id varchar(50) not null,
    prompt_title varchar(120) not null,
    prompt_text text not null,
    iteration integer not null check (iteration between 1 and 3),
    cold_start boolean not null,
    answer text not null,
    time_to_first_token_ms bigint,
    total_duration_ms bigint not null,
    load_duration_ms bigint,
    tokens_per_second double precision,
    prompt_tokens integer,
    completion_tokens integer,
    quality_score integer check (quality_score between 1 and 5),
    evaluation_notes varchar(1000),
    created_at timestamp with time zone not null
);

create index idx_benchmark_results_created_at on benchmark_results(created_at desc);
create index idx_benchmark_results_model_prompt on benchmark_results(model, prompt_id);
