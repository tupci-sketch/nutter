@php
    $parts = [
        [config('nutcms.name') . ' v' . config('nutcms.version'), __('The website, your profile, the shop and housekeeping')],
        ['Habnut Emulator', __('The game server: rooms, furni, wired, games and roleplay')],
        ['Habnut Client', __('The hotel itself, in your browser')],
    ];
@endphp

<x-ui.modal name="habnut-credits" :title="setting('hotel_name')" maxWidth="xl">
    <div class="space-y-6 text-sm text-gray-600 dark:text-gray-300">
        <p>
            {{ __('Thank you for playing :hotel. We have put a lot of effort into making the hotel what it is, and we truly appreciate you being here', ['hotel' => setting('hotel_name')]) }} ❤️
        </p>

        <div>
            <h4 class="mb-2 text-xs font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500">
                {{ __(':hotel runs on', ['hotel' => setting('hotel_name')]) }}
            </h4>

            <ul class="divide-y divide-gray-100 dark:divide-gray-700">
                @foreach ($parts as [$name, $what])
                    <li class="flex flex-col gap-0.5 py-2 sm:flex-row sm:items-baseline sm:justify-between sm:gap-4">
                        <span class="shrink-0 font-semibold text-gray-800 dark:text-gray-100">{{ $name }}</span>
                        <span class="text-gray-500 dark:text-gray-400 sm:text-right">{{ $what }}</span>
                    </li>
                @endforeach
            </ul>
        </div>

        <p class="text-xs text-gray-400 dark:text-gray-500">
            {{ __('Made by the :hotel team.', ['hotel' => setting('hotel_name')]) }}
        </p>
    </div>
</x-ui.modal>
