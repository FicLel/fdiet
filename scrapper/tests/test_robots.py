from app.net.robots import parse_robots

MERCADONA = """
User-agent: *
Allow: /$
Allow: /favicon.ico
Allow: /sitemap.xml
Allow: /product
Disallow: /
Disallow: /api
"""


def test_longest_match_wins_over_order():
    policy = parse_robots(MERCADONA, "fdiet-scrapper")
    assert policy.allowed("/product/10005/chocolate")
    assert policy.allowed("/sitemap.xml")
    assert policy.allowed("/")
    assert not policy.allowed("/api/categories/?lang=es")
    assert not policy.allowed("/legal")


def test_wildcards_and_end_anchor():
    policy = parse_robots("User-agent: *\nDisallow: /*?filter=\nDisallow: /*.php$\nDisallow: *search?q=*\n", "x")
    assert not policy.allowed("/es/fruta/?filter=bio")
    assert policy.allowed("/es/fruta/?pageNumber=2")
    assert not policy.allowed("/index.php")
    assert policy.allowed("/index.php?x=1")
    assert not policy.allowed("/q/search?q=leche")


def test_rules_after_blank_lines_still_belong_to_the_group():
    aldi = "User-agent: * \n\nSitemap: https://www.aldi.es/s.xml\n\nDisallow: /can/\n"
    policy = parse_robots(aldi, "fdiet-scrapper")
    assert not policy.allowed("/can/oferta.html")
    assert policy.allowed("/producto/cogollos-998600.html")


def test_a_group_naming_us_replaces_star_and_others_do_not_apply():
    text = "User-agent: Crawler\nDisallow: /\n\nUser-agent: *\nDisallow: /private\n\nUser-agent: fdiet-scrapper\nDisallow: /mine\nCrawl-delay: 5\n"
    ours = parse_robots(text, "fdiet-scrapper")
    assert ours.allowed("/private") and not ours.allowed("/mine")
    assert ours.crawl_delay == 5
    other = parse_robots(text, "somebody")
    assert not other.allowed("/private") and other.allowed("/anything")


def test_empty_disallow_allows_everything():
    assert parse_robots("User-agent: *\nDisallow:\n", "x").allowed("/a")
