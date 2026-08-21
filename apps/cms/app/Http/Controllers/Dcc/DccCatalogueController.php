<?php

namespace App\Http\Controllers\Dcc;

use App\Http\Controllers\Controller;
use App\Services\AuditService;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class DccCatalogueController extends Controller
{
    public function __construct(private AuditService $audit) {}

    public function index()
    {
        $pageCount = DB::table('habnut_catalogue_pages')->count();
        $itemCount = DB::table('habnut_catalogue_items')->count();
        $limitedSold = DB::table('habnut_catalogue_items')->where('limited_sells', '>', 0)->sum('limited_sells');

        return view('dcc.catalogue.index', compact('pageCount', 'itemCount', 'limitedSold'));
    }

    public function pages(Request $request)
    {
        $pages = DB::table('habnut_catalogue_pages')->orderBy('order_num')->paginate(25);

        return view('dcc.catalogue.pages', compact('pages'));
    }

    public function createPage(Request $request)
    {
        $request->validate([
            'name' => ['required', 'string', 'max:100'],
            'caption' => ['required', 'string', 'max:255'],
            'layout' => ['required', 'string'],
            'min_rank' => ['required', 'integer', 'min:1', 'max:9'],
            'order_num' => ['required', 'integer'],
        ]);

        $id = DB::table('habnut_catalogue_pages')->insertGetId([
            'name' => $request->name,
            'caption' => $request->caption,
            'layout' => $request->layout,
            'min_rank' => $request->min_rank,
            'order_num' => $request->order_num,
            'visible' => true,
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        $this->audit->log($request->user()->id, 'catalogue_page_create', 'catalogue_page', $id, ['name' => $request->name]);

        return back()->with('success', 'Page created.');
    }

    public function updatePage(Request $request, int $id)
    {
        $request->validate([
            'name' => ['required', 'string', 'max:100'],
            'caption' => ['required', 'string', 'max:255'],
            'visible' => ['boolean'],
        ]);

        DB::table('habnut_catalogue_pages')->where('id', $id)->update([
            'name' => $request->name,
            'caption' => $request->caption,
            'visible' => $request->boolean('visible'),
            'updated_at' => now(),
        ]);

        $this->audit->log($request->user()->id, 'catalogue_page_update', 'catalogue_page', $id, []);

        return back()->with('success', 'Page updated.');
    }

    public function deletePage(Request $request, int $id)
    {
        DB::table('habnut_catalogue_items')->where('page_id', $id)->delete();
        DB::table('habnut_catalogue_pages')->where('id', $id)->delete();
        $this->audit->log($request->user()->id, 'catalogue_page_delete', 'catalogue_page', $id, []);

        return back()->with('success', 'Page deleted.');
    }

    public function items(Request $request)
    {
        $query = DB::table('habnut_catalogue_items')
            ->join('habnut_catalogue_pages', 'habnut_catalogue_pages.id', '=', 'habnut_catalogue_items.page_id')
            ->select('habnut_catalogue_items.*', 'habnut_catalogue_pages.name as page_name');

        if ($request->filled('q')) {
            $query->where('habnut_catalogue_items.name', 'like', '%'.$request->q.'%');
        }

        $items = $query->paginate(25);
        $pages = DB::table('habnut_catalogue_pages')->orderBy('name')->get(['id', 'name']);

        return view('dcc.catalogue.items', compact('items', 'pages'));
    }

    public function createItem(Request $request)
    {
        $request->validate([
            'page_id' => ['required', 'integer'],
            'name' => ['required', 'string', 'max:100'],
            'base_item_id' => ['required', 'integer'],
            'cost_credits' => ['required', 'integer', 'min:0'],
            'cost_diamonds' => ['required', 'integer', 'min:0'],
            'amount' => ['required', 'integer', 'min:1'],
        ]);

        $id = DB::table('habnut_catalogue_items')->insertGetId([
            'page_id' => $request->page_id,
            'name' => $request->name,
            'base_item_id' => $request->base_item_id,
            'cost_credits' => $request->cost_credits,
            'cost_diamonds' => $request->cost_diamonds,
            'amount' => $request->amount,
            'limited_stack' => $request->input('limited_stack', 0),
            'limited_sells' => 0,
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        $this->audit->log($request->user()->id, 'catalogue_item_create', 'catalogue_item', $id, ['name' => $request->name]);

        return back()->with('success', 'Item created.');
    }

    public function updateItem(Request $request, int $id)
    {
        $request->validate([
            'cost_credits' => ['required', 'integer', 'min:0'],
            'cost_diamonds' => ['required', 'integer', 'min:0'],
        ]);

        DB::table('habnut_catalogue_items')->where('id', $id)->update([
            'cost_credits' => $request->cost_credits,
            'cost_diamonds' => $request->cost_diamonds,
            'updated_at' => now(),
        ]);

        $this->audit->log($request->user()->id, 'catalogue_item_update', 'catalogue_item', $id, []);

        return back()->with('success', 'Item updated.');
    }

    public function deleteItem(Request $request, int $id)
    {
        DB::table('habnut_catalogue_items')->where('id', $id)->delete();
        $this->audit->log($request->user()->id, 'catalogue_item_delete', 'catalogue_item', $id, []);

        return back()->with('success', 'Item deleted.');
    }

    public function limited()
    {
        $items = DB::table('habnut_catalogue_items')
            ->where('limited_stack', '>', 0)
            ->orderByRaw('(limited_stack - limited_sells) ASC')
            ->paginate(25);

        return view('dcc.catalogue.limited', compact('items'));
    }
}
