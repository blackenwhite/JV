#!/usr/bin/env python3

from pathlib import Path
import csv
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parent
DATA = ROOT / "data"
PLOTS = ROOT / "plots"


def x_column(rows):
    ms = {row["m"] for row in rows}
    ks = {row["k"] for row in rows}
    if len(ms) > 1:
        return "m"
    if len(ks) > 1:
        return "k"
    return "n"


def plot_csv(path):
    with path.open(newline="") as handle:
        rows = list(csv.DictReader(handle))
    if not rows:
        raise SystemExit(f"empty csv: {path}")
    xcol = x_column(rows)
    xs = [int(row[xcol]) for row in rows]
    measured = [float(row["measured_fpr"]) for row in rows]
    theory = [float(row["theory_fpr"]) for row in rows]
    PLOTS.mkdir(parents=True, exist_ok=True)
    fig, ax = plt.subplots()
    ax.plot(xs, measured, marker="o", label="measured")
    ax.plot(xs, theory, marker="x", label="theory")
    ax.set_xlabel(xcol)
    ax.set_ylabel("false positive rate")
    ax.set_title(path.stem)
    ax.legend()
    ax.grid(True, alpha=0.3)
    out = PLOTS / f"{path.stem}.png"
    fig.savefig(out, bbox_inches="tight")
    plt.close(fig)
    print(out)


def main():
    csvs = sorted(DATA.glob("*.csv"))
    if not csvs:
        raise SystemExit(f"no csv files in {DATA}")
    for csv_path in csvs:
        plot_csv(csv_path)


if __name__ == "__main__":
    main()