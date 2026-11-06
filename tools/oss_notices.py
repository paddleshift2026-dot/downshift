"""Third-party notices for the release APK, from Gradle's release runtime classpath and the libraries' own POMs.

    python tools/oss_notices.py        # run from the repository root, JAVA_HOME set (it calls Gradle)

Writes THIRD_PARTY_NOTICES.md (the repository) and app/src/main/assets/oss_licenses.txt (shown in the app's 세팅 tab,
"오픈소스 라이선스"). Each library's licence is read from its POM in the Gradle cache (or its parent POM); the licence
texts come from the libraries' own jars (the Apache-2.0 text AndroidX ships, NanoHTTPD's LICENSE.txt), so nothing is
typed by hand. The Pleos Connect SDK is not open source and is listed apart.
"""
import glob
import os
import re
import subprocess
import sys
import zipfile
from collections import OrderedDict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = Path(os.path.expanduser("~/.gradle/caches/modules-2/files-2.1"))
PLEOS = "ai.pleos.playground"


def release_deps():
    gradlew = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    tree = subprocess.run([gradlew, "-q", ":app:dependencies", "--configuration", "releaseRuntimeClasspath"],
                          cwd=ROOT, capture_output=True, text=True, encoding="utf-8", check=True).stdout
    deps = {}
    for line in tree.splitlines():
        m = re.search(r"--- ([\w.-]+):([\w.-]+)(?::([\w.-]+))?(?: -> ([\w.-]+))?", line)
        if m and m.group(1) != "project":
            g, a, v, v2 = m.groups()
            deps[(g, a)] = v2 or v
    return deps


def pom_licences(g, a, v, depth=0):
    poms = glob.glob(str(CACHE / g / a / v / "*" / f"{a}-{v}.pom"))
    if not poms:
        return []
    x = open(poms[0], encoding="utf-8", errors="replace").read()
    found = re.findall(r"<license>.*?<name>(.*?)</name>.*?(?:<url>(.*?)</url>)?.*?</license>", x, re.S)
    if found:
        return [(n.strip(), (u or "").strip()) for n, u in found]
    p = re.search(r"<parent>.*?<groupId>(.*?)</groupId>.*?<artifactId>(.*?)</artifactId>.*?<version>(.*?)</version>", x, re.S)
    return pom_licences(p.group(1).strip(), p.group(2).strip(), p.group(3).strip(), depth + 1) if p and depth < 3 else []


def kind(names):
    s = " ".join(names).lower()
    if "apache" in s:
        return "Apache-2.0"
    if "bsd" in s and "3" in s:
        return "BSD-3-Clause"
    return "/".join(names) or "?"


def jar_text(pattern, entry_end, must):
    for jar in glob.glob(str(CACHE / pattern), recursive=True):
        with zipfile.ZipFile(jar) as z:
            for n in z.namelist():
                if n.upper().endswith(entry_end):
                    t = z.read(n).decode("utf-8", "replace")
                    if must in t:
                        return t.strip() + "\n"
    sys.exit(f"no licence text for {pattern}")


def main():
    deps = release_deps()
    groups = OrderedDict()  # licence -> {group: [artifact version]}
    pleos = []
    for (g, a), v in sorted(deps.items()):
        if g == PLEOS:
            pleos.append(f"{a} {v}")
            continue
        groups.setdefault(kind([n for n, _ in pom_licences(g, a, v)]), OrderedDict()).setdefault(g, []).append(f"{a} {v}")
    apache = jar_text("androidx.annotation/annotation-jvm/**/*.jar", "LICENSE.TXT", "Version 2.0, January 2004")
    bsd = jar_text("org.nanohttpd/nanohttpd/2.3.1/**/*.jar", "LICENSE.TXT", "Redistribution and use")
    unknown = [k for k in groups if k not in ("Apache-2.0", "BSD-3-Clause")]
    if unknown:
        sys.exit(f"licences to check by hand: {unknown}")

    md = ["# 제3자 소프트웨어 고지 (Third-party notices)", "",
          "Downshift release APK에 들어가는 외부 라이브러리와 라이선스입니다. Gradle의 release 런타임 의존성과 각 라이브러리의 POM에서 "
          "`tools/oss_notices.py`로 만들었고, 같은 내용이 앱의 세팅 탭 \"오픈소스 라이선스\"에도 있습니다.", "",
          "## Pleos Connect SDK (오픈소스 아님)", "",
          "현대자동차그룹 Pleos Playground의 SDK입니다. 공모전 개발 환경으로 제공된 것을 그 이용 조건에 따라 씁니다(Maven `ai.pleos.playground`).", "",
          "- " + ", ".join(pleos), ""]
    txt = ["Downshift 오픈소스 라이선스", "", "Pleos Connect SDK (현대자동차그룹 Pleos Playground, 오픈소스 아님): " + ", ".join(pleos), ""]
    for lic in ("BSD-3-Clause", "Apache-2.0"):
        md += [f"## {lic}", ""]
        txt += [f"[{lic}]"]
        for g, arts in groups.get(lic, {}).items():
            md.append(f"- `{g}`: " + ", ".join(arts))
            txt.append(f"- {g}: " + ", ".join(arts))
        md.append("")
        txt.append("")
    md += ["## 라이선스 원문", "", "### BSD-3-Clause (NanoHTTPD)", "", "```", bsd.rstrip(), "```", "",
           "### Apache License 2.0", "", "```", apache.rstrip(), "```", ""]
    txt += ["== BSD-3-Clause (NanoHTTPD) ==", "", bsd, "== Apache License 2.0 ==", "", apache]
    (ROOT / "THIRD_PARTY_NOTICES.md").write_text("\n".join(md), encoding="utf-8", newline="\n")
    (ROOT / "app/src/main/assets/oss_licenses.txt").write_text("\n".join(txt), encoding="utf-8", newline="\n")
    n = sum(len(a) for gs in groups.values() for a in gs.values())
    print(f"THIRD_PARTY_NOTICES.md and assets/oss_licenses.txt: {n} open-source artifacts + {len(pleos)} Pleos SDK artifacts")


if __name__ == "__main__":
    main()
