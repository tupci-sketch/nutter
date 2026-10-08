<?php

namespace App\Http\Controllers\Client;

use App\Http\Controllers\Controller;
use App\Support\AuthenticatedUser;
use Illuminate\Http\Request;
use Illuminate\View\View;
use App\Support\HabnutWorlds;

/** The hotel's entrance: Habnut, the classic hotel. */
class NitroController extends Controller
{
    public function __invoke(Request $request): View
    {
        $user = AuthenticatedUser::from($request);
        $user->update([
            'ip_current' => $request->ip(),
        ]);
        HabnutWorlds::enter($user->id, HabnutWorlds::HOTEL);

        return view('client.nitro', [
            'sso' => $user->ssoTicket(),
            'world' => HabnutWorlds::HOTEL,
        ]);
    }
}
