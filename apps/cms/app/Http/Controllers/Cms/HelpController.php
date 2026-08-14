<?php

namespace App\Http\Controllers\Cms;

use App\Http\Controllers\Controller;

class HelpController extends Controller
{
    private static array $articles = [
        'getting-started' => [
            'title' => 'Getting Started',
            'body'  => 'Welcome to Habnut! Learn how to create your account and get started in the hotel.',
        ],
        'currency'        => [
            'title' => 'Currency Guide',
            'body'  => 'Habnut has several currencies: Credits (primary), Diamonds (premium), and Nut Points (engagement).',
        ],
        'rooms'           => [
            'title' => 'Rooms & Navigation',
            'body'  => 'Learn how to navigate the hotel, create rooms, and manage your spaces.',
        ],
        'safety'          => [
            'title' => 'Safety & Reporting',
            'body'  => 'Your safety is our priority. Learn how to report issues and block users.',
        ],
    ];

    public function index()
    {
        $articles = self::$articles;
        return view('cms.help.index', compact('articles'));
    }

    public function show(string $slug)
    {
        $article = self::$articles[$slug] ?? abort(404);
        return view('cms.help.show', compact('article', 'slug'));
    }
}
