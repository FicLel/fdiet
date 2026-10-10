"""robots.txt per RFC 9309: groups, longest match wins, `*` and `$` wildcards.

The standard library's `urllib.robotparser` matches by plain prefix and takes the first
rule that matches, so `Disallow: /*?filter=` never applies and an earlier short `Allow`
beats a later, more specific `Disallow`. Supermarket robots files lean on both.
"""

import re
from dataclasses import dataclass, field


@dataclass(frozen=True)
class _Rule:
    allow: bool
    pattern: str
    regex: re.Pattern[str]


def _compile(pattern: str) -> re.Pattern[str]:
    anchored = pattern.endswith("$")
    body = pattern[:-1] if anchored else pattern
    regex = ".*".join(re.escape(part) for part in body.split("*"))
    return re.compile(regex + ("$" if anchored else ""))


@dataclass
class _Group:
    agents: list[str] = field(default_factory=list)
    rules: list[_Rule] = field(default_factory=list)
    crawl_delay: float | None = None


@dataclass
class RobotsPolicy:
    rules: list[_Rule]
    crawl_delay: float | None = None

    @classmethod
    def allow_all(cls) -> "RobotsPolicy":
        return cls(rules=[])

    @classmethod
    def disallow_all(cls) -> "RobotsPolicy":
        return cls(rules=[_Rule(False, "/", _compile("/"))])

    def allowed(self, path_and_query: str) -> bool:
        if path_and_query == "/robots.txt":
            return True
        best: _Rule | None = None
        for rule in self.rules:
            if not rule.regex.match(path_and_query):
                continue
            if (
                best is None
                or len(rule.pattern) > len(best.pattern)
                or (len(rule.pattern) == len(best.pattern) and rule.allow)
            ):
                best = rule
        return best is None or best.allow


def parse_robots(text: str, product_token: str) -> RobotsPolicy:
    """The policy for `product_token` (e.g. 'fdiet-scrapper'): every group naming it, else '*'."""
    groups: list[_Group] = []
    current: _Group | None = None
    last_was_agent = False

    for raw_line in text.splitlines():
        line = raw_line.split("#", 1)[0].strip()
        if ":" not in line:
            continue
        key, value = (part.strip() for part in line.split(":", 1))
        key = key.lower()
        if key == "user-agent":
            if current is None or not last_was_agent:
                current = _Group()
                groups.append(current)
            current.agents.append(value.lower())
            last_was_agent = True
            continue
        last_was_agent = False
        if current is None:
            continue  # Sitemap: and friends outside any group
        if key in ("allow", "disallow"):
            if value:  # an empty Disallow allows everything: no rule
                current.rules.append(_Rule(key == "allow", value, _compile(value)))
        elif key == "crawl-delay":
            try:
                current.crawl_delay = float(value)
            except ValueError:
                pass

    token = product_token.lower()
    named = [g for g in groups if token in g.agents]
    chosen = named or [g for g in groups if "*" in g.agents]
    delays = [g.crawl_delay for g in chosen if g.crawl_delay is not None]
    return RobotsPolicy(
        rules=[rule for group in chosen for rule in group.rules],
        crawl_delay=max(delays) if delays else None,
    )
