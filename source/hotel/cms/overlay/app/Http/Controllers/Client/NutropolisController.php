<?php

namespace App\Http\Controllers\Client;

use App\Http\Controllers\Controller;
use App\Support\AuthenticatedUser;
use Illuminate\Http\Request;
use Illuminate\View\View;
use App\Support\HabnutWorlds;

/** The city's entrance: Nutropolis, the roleplay world. */
class NutropolisController extends Controller
{
    public function __invoke(Request $request): View
    {
        $user = AuthenticatedUser::from($request);
        $user->update([
            'ip_current' => $request->ip(),
        ]);
        HabnutWorlds::enter($user->id, HabnutWorlds::CITY);

        return view('client.nitro', [
            'sso' => $user->ssoTicket(),
            'world' => HabnutWorlds::CITY,
        ]);
    }
}
