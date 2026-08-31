<?php

namespace App\Support;

/**
 * URLs for pictures the hotel renders from its own asset pack.
 *
 * Every figure and badge shown on the website goes through here, so the site
 * has one place that knows the imager's address and one place to change if a
 * hotel puts it somewhere else.
 */
class Imager
{
    /** Sizes a caller may ask for, mapped to what the imager understands. */
    private const SIZES = [
        // The small head used in lists and next to forum posts.
        's' => ['size' => 's', 'headonly' => 1, 'scale' => 1],
        // The full figure, as drawn in a room.
        'm' => ['size' => 'n', 'headonly' => 0, 'scale' => 1],
        // The full figure at double size, for profile pages.
        'l' => ['size' => 'n', 'headonly' => 0, 'scale' => 2],
    ];

    /**
     * URL of a rendered figure, or null when the hotel has no imager.
     *
     * A blank figure returns null rather than a URL that would render nothing:
     * the caller then shows its own fallback instead of a broken picture.
     */
    public static function avatar(?string $figure, string $size = 'm', int $direction = 2): ?string
    {
        $base = self::base();
        if ($base === null || $figure === null || $figure === '') {
            return null;
        }

        $options = self::SIZES[$size] ?? self::SIZES['m'];

        return $base.'/avatar.png?'.http_build_query([
            'figure' => $figure,
            'direction' => $direction,
            'head_direction' => $direction,
            'size' => $options['size'],
            'headonly' => $options['headonly'],
            'scale' => $options['scale'],
        ]);
    }

    /**
     * URL of a badge picture.
     *
     * The same route serves achievement badges, which are named, and group
     * badges, which are codes describing how to build the picture. Callers hold
     * both kinds in the same column and cannot tell them apart, so neither can
     * this.
     */
    public static function badge(?string $badge, int $scale = 1): ?string
    {
        $base = self::base();
        if ($base === null || $badge === null || $badge === '') {
            return null;
        }

        $url = $base.'/badge/'.rawurlencode($badge).'.png';

        return $scale > 1 ? $url.'?scale='.$scale : $url;
    }

    /** The imager's address, or null when a hotel has turned it off. */
    private static function base(): ?string
    {
        $configured = config('habnut.imager_url');

        return $configured ? rtrim($configured, '/') : null;
    }
}
