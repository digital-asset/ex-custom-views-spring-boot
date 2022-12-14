-- Create events table
create table events
(
    contract_id varchar not null,
    event_id    varchar not null,
    currency    varchar not null,
    amount      numeric not null
);
