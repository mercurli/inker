import contextlib
import io
import json
import re
import sys

import akshare as ak


def normalize_code(value):
    if value is None:
        return None
    text = str(value).strip()
    match = re.search(r"(\d{6})$", text)
    return match.group(1) if match else None


def main():
    with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
        df = ak.stock_zh_a_spot()
    result = []

    for _, row in df.iterrows():
        code = normalize_code(row.get("代码"))
        if not code:
            continue

        latest_price = row.get("最新价")
        change_percent = row.get("涨跌幅")

        result.append(
            {
                "code": code,
                "latestPrice": None if latest_price is None else float(latest_price),
                "changePercent": None if change_percent is None else float(change_percent),
            }
        )

    sys.stdout.write(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
