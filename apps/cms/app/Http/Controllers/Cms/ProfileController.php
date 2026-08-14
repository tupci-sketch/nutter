<?php

namespace App\Http\Controllers\Cms;

use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\Rule;

class ProfileController extends Controller
{
    public function show(Request $request)
    {
        return view('cms.profile.show', ['user' => $request->user()]);
    }

    public function update(Request $request)
    {
        $user = $request->user();

        $request->validate([
            'email'    => ['required', 'email', Rule::unique('users')->ignore($user->id)],
            'password' => ['nullable', 'confirmed', 'min:8'],
        ]);

        $data = ['email' => $request->email];
        if ($request->filled('password')) {
            $data['password'] = Hash::make($request->password);
        }

        $user->update($data);

        return back()->with('success', 'Profile updated.');
    }

    public function updateMotto(Request $request)
    {
        $request->validate(['motto' => ['required', 'string', 'max:255']]);
        $request->user()->update(['motto' => $request->motto]);
        return back()->with('success', 'Motto updated.');
    }

    public function view(string $username)
    {
        $user = User::where('username', $username)->firstOrFail();
        return view('cms.profile.view', compact('user'));
    }
}
