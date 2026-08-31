<?php

namespace App\Http\Controllers\Cms;

use App\Http\Controllers\Controller;
use App\Models\NewsArticle;

class NewsController extends Controller
{
    public function index()
    {
        $articles = NewsArticle::published()->latest('published_at')->paginate(10);

        return view('cms.news.index', compact('articles'));
    }

    public function show(string $slug)
    {
        $article = NewsArticle::published()->where('slug', $slug)->firstOrFail();

        return view('cms.news.show', compact('article'));
    }
}
