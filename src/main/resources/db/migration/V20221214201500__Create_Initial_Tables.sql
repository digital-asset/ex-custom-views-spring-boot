-- Create events table
create table events
(
    contract_id varchar not null,
    event_id    varchar not null,
    currency    varchar not null,
    amount      numeric not null,
    json_data   jsonb not null
);

CREATE INDEX json_data_observers_gin ON events USING gin((json_data->'observers'));
