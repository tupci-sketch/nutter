<?php

namespace App\Http\Controllers\Cms;

use App\Http\Controllers\Controller;
use App\Models\NewsArticle;

class HomeController extends Controller
{
    public function index()
    {
        $articles = NewsArticle::published()->latest('published_at')->take(3)->get();
        return view('cms.home', compact('articles'));
    }

    public function hotel()
    {
        return view('cms.hotel');
    }
}
