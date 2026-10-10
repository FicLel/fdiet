import json
import logging

from app import logs


def test_a_json_line_carries_the_bound_context_and_the_extras():
    logs.bind(sync_id="s1", supermarket="aldi", job_id=None)
    record = logging.LogRecord("app.jobs", logging.INFO, __file__, 1, "%d products", (250,), None)
    record.requests = 300
    line = json.loads(logs.JsonFormatter().format(record))
    assert line["message"] == "250 products"
    assert line["sync_id"] == "s1" and line["supermarket"] == "aldi" and "job_id" not in line
    assert line["requests"] == 300 and line["level"] == "INFO" and line["@timestamp"].endswith("+00:00")
    logs.bind(sync_id=None, supermarket=None)


def test_a_text_line_names_its_context():
    logs.bind(sync_id="s2")
    record = logging.LogRecord("app.jobs", logging.WARNING, __file__, 1, "slow", (), None)
    assert "[sync_id=s2] slow" in logs.TextFormatter().format(record)
    logs.bind(sync_id=None)
