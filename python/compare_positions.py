import sys
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np

DEFAULT_ORBIT_1 = Path(__file__).resolve().parent.parent / "out" / "orbit.csv"
DEFAULT_ORBIT_2 = Path(__file__).resolve().parent.parent / "out" / "orbit-stk.csv"


def load_orbit(path: Path) -> tuple[np.ndarray, np.ndarray]:
    data = np.loadtxt(path, delimiter=",", skiprows=1)
    return data[:, 0], data[:, 1:4]


def main() -> None:
    path_1 = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_ORBIT_1
    path_2 = Path(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_ORBIT_2

    elapsed_s_1, xyz_m_1 = load_orbit(path_1)
    elapsed_s_2, xyz_m_2 = load_orbit(path_2)

    if elapsed_s_1.shape != elapsed_s_2.shape or not np.allclose(elapsed_s_1, elapsed_s_2):
        raise ValueError("Input files must share the same timestamps.")

    #diff_m = np.linalg.norm(xyz_m_2 - xyz_m_1, axis=1)
    diff_xyz = xyz_m_1 - xyz_m_2
    max_val = np.max(diff_xyz)
    min_val = np.min(diff_xyz)

    plt.style.use("seaborn-v0_8-whitegrid")
    fig, ax = plt.subplots(figsize=(9, 5))

    time_h = elapsed_s_1 / 3600
    ax.plot(time_h, diff_xyz[:, 0], label="x", linewidth=1)
    ax.plot(time_h, diff_xyz[:, 1], label="y", linewidth=1)
    ax.plot(time_h, diff_xyz[:, 2], label="z", linewidth=1)

    ax.axhline(max_val, color="black", linestyle="--", linewidth=0.8, alpha=0.6)
    ax.axhline(min_val, color="black", linestyle="--", linewidth=0.8, alpha=0.6)

    current_ticks = ax.get_yticks()
    ax.set_yticks(np.append(current_ticks, [min_val, max_val]))


    #ax.plot(elapsed_s_1 / 3600, diff_m, color="#d62728", linewidth=2)
    ax.set_xlabel("elapsed time [h]")
    ax.set_ylabel("position difference [m]")
    ax.set_title(f"Position difference: {path_1.name} vs {path_2.name}")
    ax.legend(loc="lower right")
    fig.tight_layout()

    out_path = path_1.with_name("position-difference.pdf")
    fig.savefig(out_path)
    plt.show()
    print(f"wrote {out_path}")


if __name__ == "__main__":
    main()
