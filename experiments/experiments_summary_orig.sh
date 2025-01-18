#!/bin/bash

TIMESTAMP=$(date +"%Y%m%d_%H%M%S") 
OUTPUT_FILE="results/results_orig_$TIMESTAMP.csv" 
OUTPUT_SUMMARIZED_FILE="results/results_summarized_orig_$TIMESTAMP.csv" 
TMP_SQL_FILE="queries_orig.sql" 

cat <<EOT > $TMP_SQL_FILE
CREATE OR REPLACE TABLE detailed_experiments AS
SELECT
    *,
    split_part(split_part(filename, 'orig\', 2), '__', 1) AS dataset,
    split_part(split_part(filename, 'algo_', 2), '__swap', 1) AS algo,
    split_part(split_part(filename, 'swap_', 2), '_3_0.3', 1) AS swap,
    ROW_NUMBER() OVER () rn
FROM
    read_csv_auto('output/orig/**', delim=',', header=FALSE, filename=TRUE);

ALTER TABLE detailed_experiments RENAME COLUMN column0 TO case_id;
ALTER TABLE detailed_experiments RENAME COLUMN column1 TO activity;
ALTER TABLE detailed_experiments RENAME COLUMN column2 TO cost;
ALTER TABLE detailed_experiments RENAME COLUMN column3 TO time_ms;
ALTER TABLE detailed_experiments RENAME COLUMN column4 TO timestamp;


CREATE OR REPLACE TABLE aggregated_experiments AS 
SELECT 
    case_id,
    filename,
    dataset,
    algo,
    swap,
    SUM(time_ms) AS total_time_ms,
    MAX(cost) AS cost,
    COUNT(*) AS event_count
FROM (
    SELECT 
        * EXCLUDE(cost),
        LAST_VALUE(cost) OVER (
            PARTITION BY filename, case_id 
            ORDER BY rn
            ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
        ) AS cost
    FROM detailed_experiments
) sub
GROUP BY ALL
ORDER BY case_id, filename;

CREATE OR REPLACE TABLE summarized_experiments AS
SELECT 
    *, 
    total_time_ms/event_count AS time_per_event_ms, 
    cost/case_count AS cost_per_trace
    FROM (
        SELECT 
            dataset, 
            algo, 
            swap, 
            SUM(total_time_ms) AS total_time_ms, 
            SUM(cost) AS cost, 
            SUM(event_count) AS event_count, 
            300 AS case_count 
        FROM aggregated_experiments 
        GROUP BY ALL
    )
ORDER BY dataset, swap, algo;

.mode csv

.once $OUTPUT_FILE

SELECT * FROM aggregated_experiments;

.once $OUTPUT_SUMMARIZED_FILE

SELECT * FROM summarized_experiments;

EOT

duckdb < $TMP_SQL_FILE

rm $TMP_SQL_FILE

echo "DuckDB queries executed. Results saved to $OUTPUT_FILE."
