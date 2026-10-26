"""Parse production-generated JSON and check the recorded crash path is absent.

This verifies payload data on the host, not Xiaomi SystemUI rendering.
"""
import json
import subprocess
import sys

classpath = sys.argv[1]
picture = "miui.focus.pic_focusallowlist_v2"
maximum = 12 * 60 * 60 * 1000
cases = 0


def walk(value):
    if isinstance(value, dict):
        for key, item in value.items():
            assert key not in {"actions", "actionInfo", "actionArea", "action", "actionTitle", "timerInfo"}, key
            if key in {"title", "content", "digit", "ticker", "aodTitle"}:
                assert isinstance(item, str) and item.strip(), key
            if key in {"pic", "picDark", "tickerPic", "aodPic"}:
                assert item == picture, (key, item)
            assert item is not None
            walk(item)
    elif isinstance(value, list):
        for item in value:
            walk(item)


for protocol, values in [
    (3, [-1, 0, 1, 999, 1000, 60000, 1200000, 7200000, maximum, maximum + 1, 9223372036854775807]),
    (0, [60000]), (2, [60000]), (4, [60000]),
]:
    result = subprocess.run(
        ["java", "-cp", classpath, "dev.lance.focusallowlist.IslandTemplateDump", str(protocol), *map(str, values)],
        text=True, capture_output=True, check=True,
    )
    rows = [json.loads(line) for line in result.stdout.splitlines()]
    assert len(rows) == len(values)
    for remaining, row in zip(values, rows):
        cases += 1
        assert row["remaining"] == remaining
        payload = row["payload"]
        supported = protocol == 3 and 0 < remaining <= maximum
        assert (payload is not None) == supported
        assert row["delay"] == max(250, min(1000 if supported else 30000, remaining))
        if not supported:
            continue
        walk(payload)
        assert len(json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode()) <= 3072
        focus = payload["param_v2"]
        assert focus["protocol"] == 1
        assert focus["updatable"] is True and focus["reopen"] == "reopen"
        assert focus["islandFirstFloat"] is False and focus["enableFloat"] is False
        assert focus["filterWhenNoPermission"] is False
        assert focus["baseInfo"]["type"] == 1
        island = focus["param_island"]
        assert island["islandProperty"] == 1
        assert island["islandTimeout"] == (remaining + 999) // 1000
        assert focus["timeout"] == (remaining + 59999) // 60000 + 1
        seconds = (remaining + 999) // 1000
        digits = f"{seconds // 60:02d}:{seconds % 60:02d}"
        assert island["bigIslandArea"]["sameWidthDigitInfo"]["digit"] == digits
        assert focus["baseInfo"]["title"] == f"剩余 {digits}"
        assert island["smallIslandArea"]["picInfo"]["pic"] == picture

print(f"PASS {cases} production island JSON fixtures: no buttons, no vendor timer, valid texts/pictures/timeouts/fallback")
