<?php
/*
 * Turns the Atom CMS source into NutCMS, the Habnut website.
 *
 *     php rebrand.php <source-dir>
 *
 * Runs at image build time, after the overlay is copied in. Only text people
 * can see is changed: translation strings, page text, the admin panel's name.
 * Identifiers (config('habbo.*'), class names, routes) are left alone, so the
 * code keeps working exactly as upstream wrote it.
 */

const PRODUCT = 'NutCMS';
const VERSION = '1.0';

$root = rtrim($argv[1] ?? '', '/');
if ($root === '' || ! is_dir("$root/resources")) {
    fwrite(STDERR, "usage: php rebrand.php <atom-source-dir>\n");
    exit(1);
}

$changed = 0;

/** Habbo -> Habnut, keeping the case of the original, but never inside a URL. */
function habnut(string $s): string
{
    $parts = preg_split('~(https?://\S+)~', $s, -1, PREG_SPLIT_DELIM_CAPTURE);
    foreach ($parts as $i => $p) {
        if (str_starts_with($p, 'http')) {
            continue;
        }
        $parts[$i] = preg_replace_callback('/\\bnitro\\b/i', function ($m) {
            $t = $m[0];
            if (strtoupper($t) === $t) {
                return 'NUTTY';
            }

            return ctype_upper($t[0]) ? 'Nutty' : 'nutty';
        }, $parts[$i]);
        $parts[$i] = preg_replace_callback('/habb[oóòôö]/iu', function ($m) {
            $t = $m[0];
            if (mb_strtoupper($t) === $t) {
                return 'HABNUT';
            }

            return ctype_upper($t[0]) ? 'Habnut' : 'habnut';
        }, $parts[$i]);
        // whatever is left once Habbo is done: Habbicons -> Nuticons
        $parts[$i] = preg_replace_callback('/habb/i', function ($m) {
            $t = $m[0];
            if (strtoupper($t) === $t) {
                return 'NUT';
            }

            return ctype_upper($t[0]) ? 'Nut' : 'nut';
        }, $parts[$i]);
    }

    return implode('', $parts);
}

function product(string $s): string
{
    return preg_replace('/\bAtom ?CMS\b/i', PRODUCT, $s);
}

/** Applies $fn to the contents of a quoted string literal passed to __() or @lang(). */
function inTranslations(string $code, callable $fn): string
{
    return preg_replace_callback(
        '/(__\(|@lang\(|trans\()\s*([\'"])((?:\\\\.|(?!\2).)*)\2/s',
        fn ($m) => $m[1] . $m[2] . $fn($m[3]) . $m[2],
        $code
    );
}

function rewrite(string $path, callable $fn): void
{
    global $changed;
    $before = file_get_contents($path);
    $after = $fn($before);
    if ($after !== $before) {
        file_put_contents($path, $after);
        $changed++;
    }
}

$files = new RecursiveIteratorIterator(new RecursiveDirectoryIterator($root, FilesystemIterator::SKIP_DOTS));
foreach ($files as $file) {
    $path = $file->getPathname();
    $rel = substr($path, strlen($root) + 1);
    if (preg_match('~^(vendor|node_modules|storage|tests|\.git)/~', $rel)) {
        continue;
    }

    // Translation files: every key and value is text someone reads.
    if (preg_match('~^lang/[^/]+\.json$~', $rel)) {
        rewrite($path, function ($s) {
            $data = json_decode($s, true);
            if (! is_array($data)) {
                return $s;
            }
            $out = [];
            foreach ($data as $k => $v) {
                $out[habnut(product($k))] = is_string($v) ? habnut(product($v)) : $v;
            }

            return json_encode($out, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES) . "\n";
        });
        continue;
    }
    if (preg_match('~^lang/.+\.php$~', $rel)) {
        rewrite($path, fn ($s) => preg_replace_callback("/=>\s*'((?:\\\\.|[^'])*)'/", fn ($m) => "=> '" . habnut(product($m[1])) . "'", $s));
        continue;
    }

    // Pages: product name anywhere, Habbo only inside translated strings.
    if (str_ends_with($rel, '.blade.php')) {
        rewrite($path, fn ($s) => product(inTranslations($s, fn ($t) => habnut(product($t)))));
        continue;
    }

    // Application code: only the strings it shows people.
    if (preg_match('~^(app|config)/.+\.php$~', $rel)) {
        rewrite($path, fn ($s) => inTranslations(preg_replace("/'Atom CMS'/", "'" . PRODUCT . "'", $s), fn ($t) => habnut(product($t))));
    }
}

// The greeting in the browser's developer console.
foreach (glob("$root/resources/themes/*/js/app.js") as $js) {
    rewrite($js, fn ($s) => preg_replace(
        '/"%cAtom CMS%c[^"]*"/',
        '"%c' . PRODUCT . ' v' . VERSION . '%c\n\nHabnut\'s website. Poking around? Come and say hello in the hotel.\n\n"',
        $s
    ));
}

// Search engines and the curious see what runs the site.
$generator = '<meta name="generator" content="' . PRODUCT . ' v' . VERSION . '">';
foreach (glob("$root/resources/themes/*/views/layouts/*.blade.php") as $layout) {
    rewrite($layout, fn ($s) => str_contains($s, 'name="generator"') ? $s
        : preg_replace('~(<meta name="csrf-token"[^>]*>)~', "$1\n    $generator", $s, 1));
}

// Keywords: the hotel's own name, not the game it is a retro of.
foreach (glob("$root/resources/themes/*/views/components/seo-meta.blade.php") as $seo) {
    rewrite($seo, fn ($s) => str_replace("'habbo', 'retro hotel'", "'habnut', 'retro hotel'", $s));
}

// The credits dialog is NutCMS's now.
foreach (glob("$root/resources/{views,themes/*/views}/{components,components/*}/*.blade.php", GLOB_BRACE) as $view) {
    rewrite($view, fn ($s) => str_replace("'atom-credits'", "'habnut-credits'", $s));
}

// The button into the hotel says where it goes, not which client it runs.
// Discord links appear only once the hotel has a server to link to.
foreach (glob("$root/resources/themes/*/views/{,*/,*/*/}*.blade.php", GLOB_BRACE) as $view) {
    rewrite($view, function ($s) {
        $s = str_replace("__('Nitro client')", "__('Enter :hotel', ['hotel' => setting('hotel_name')])", $s);
        $s = preg_replace(
            '~(<a href="\{\{ setting\(\'discord_invitation_link\'\) \}\}"[^>]*>\s*\{\{ __\(\'Discord\'\) \}\}\s*</a>)~',
            "@if (setting('discord_invitation_link'))\n    $1\n    @endif",
            $s
        );

        return preg_replace('~(<x-user\.discord-widget\s*/>)~', "@if (setting('discord_widget_id'))\n            $1\n            @endif", $s);
    });
}

// The game client is Nutty: its page lives at /game/nutty, and says so.
rewrite("$root/routes/web.php", fn ($s) => str_replace("Route::get('/nitro', NitroController::class)", "Route::get('/nutty', NitroController::class)", $s));
foreach (glob("$root/resources/themes/*/views/client/nitro.blade.php") as $view) {
    rewrite($view, fn ($s) => str_replace(
        [" - Nitro</title>", 'id="nitro-client"', 'iframe id="nitro"'],
        ["</title>", 'id="nutty-client"', 'iframe id="nutty"'],
        $s
    ));
}
foreach (glob("$root/resources/themes/*/views/public/assets/js/*.js") as $js) {
    rewrite($js, fn ($s) => str_replace('getElementById("nitro")', 'getElementById("nutty")', $s));
}

// Habnut's type, served from the site itself: Nunito for text, Lilita One
// for headings. No third-party font requests.
$fontFaces = <<<'CSS'
@font-face { font-family: "Nunito"; font-weight: 400; font-style: normal; font-display: swap; src: url("/assets/fonts/nunito-latin-400-normal.woff2") format("woff2"); }
@font-face { font-family: "Nunito"; font-weight: 400; font-style: italic; font-display: swap; src: url("/assets/fonts/nunito-latin-400-italic.woff2") format("woff2"); }
@font-face { font-family: "Nunito"; font-weight: 500 700; font-style: normal; font-display: swap; src: url("/assets/fonts/nunito-latin-700-normal.woff2") format("woff2"); }
@font-face { font-family: "Nunito"; font-weight: 800 900; font-style: normal; font-display: swap; src: url("/assets/fonts/nunito-latin-800-normal.woff2") format("woff2"); }
@font-face { font-family: "Lilita One"; font-weight: 400; font-style: normal; font-display: swap; src: url("/assets/fonts/lilita-one-latin-400-normal.woff2") format("woff2"); }
h1, h2, .font-heading { font-family: "Lilita One", "Nunito", sans-serif; font-weight: 400; letter-spacing: 0.01em; }
CSS;
foreach (glob("$root/resources/themes/*/css/app.css") as $css) {
    rewrite($css, function ($s) use ($fontFaces) {
        $s = preg_replace('~@import\s+"https://fonts\.googleapis\.com/[^"]*";\s*~', '', $s);
        $s = preg_replace('~font-family:\s*poppins,\s*sans-serif;~i', 'font-family: "Nunito", sans-serif;', $s);

        if (str_contains($s, 'Lilita One')) {
            return $s;
        }
        // after the @imports, which CSS insists come first
        return preg_match_all('~^@import[^\n]*\n~m', $s, $m, PREG_OFFSET_CAPTURE)
            ? substr_replace($s, $fontFaces . "\n", end($m[0])[1] + strlen(end($m[0])[0]), 0)
            : $fontFaces . "\n" . $s;
    });
}
foreach (glob("$root/resources/themes/*/views/layouts/*.blade.php") as $layout) {
    rewrite($layout, fn ($s) => preg_replace(
        ['~\s*<link rel="stylesheet" href="https://fonts\.googleapis\.com/[^"]*">~',
         '~<link rel="icon" type="image/gif" sizes="18x17" href="\{\{ asset\(\'assets/images/home_icon\.gif\'\) \}\}">~'],
        ['', '<link rel="icon" type="image/png" sizes="32x32" href="{{ asset(\'assets/images/habnut/icon-32.png\') }}">'
            . "\n    " . '<link rel="apple-touch-icon" href="{{ asset(\'assets/images/habnut/icon-192.png\') }}">'],
        $s
    ));
}

// The logo generator offered the original hotel's lettering: not any more.
foreach (glob("$root/resources/themes/*/views/logo-generator.blade.php") as $view) {
    rewrite($view, fn ($s) => preg_replace(
        '~\s*<div x-bind:class="\{[^"]*fontType === \'(habbo_modern|habton|habton_capitalized)\'\}"[\s\S]*?</div>~', '', $s));
}
// Atom's own letter set keeps its letters under a plain name.
foreach (glob("$root/resources/themes/*/views/logo-generator.blade.php") as $view) {
    rewrite($view, fn ($s) => str_replace(
        ["logo-generator/atom/", "fontType === 'atom'", "selectFont('atom')", "fontType: 'atom'"],
        ["logo-generator/blue/", "fontType === 'blue'", "selectFont('blue')", "fontType: 'blue'"], $s));
}
if (is_dir("$root/public/assets/images/logo-generator/atom")) {
    rename("$root/public/assets/images/logo-generator/atom", "$root/public/assets/images/logo-generator/blue");
}
foreach (['habbo_modern', 'habton', 'habton_capitalized'] as $set) {
    @exec('rm -rf ' . escapeshellarg("$root/public/assets/images/logo-generator/$set"));
}

// The admin panel is NutCMS Housekeeping.
rewrite("$root/app/Providers/Filament/AdminFilamentPanelProvider.php", fn ($s) => str_contains($s, '->brandName(') ? $s
    : str_replace("->path('housekeeping')", "->path('housekeeping')\n            ->brandName('" . PRODUCT . " Housekeeping')", $s));

// Images named after their artist get Habnut names.
@mkdir("$root/public/assets/images/habnut", 0755, true);
foreach (['kasja_mepage_header.png' => 'me-header.png', 'kasja_mepage_image.png' => 'me-image.png'] as $from => $to) {
    if (is_file("$root/public/assets/images/$from") && ! is_file("$root/public/assets/images/habnut/$to")) {
        copy("$root/public/assets/images/$from", "$root/public/assets/images/habnut/$to");
    }
}

file_put_contents("$root/config/nutcms.php", "<?php\n\nreturn ['name' => '" . PRODUCT . "', 'version' => '" . VERSION . "'];\n");

echo PRODUCT . ' v' . VERSION . ": rebranded $changed files\n";
