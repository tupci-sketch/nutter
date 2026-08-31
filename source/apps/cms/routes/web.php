<?php

use App\Http\Controllers\Auth\LoginController;
use App\Http\Controllers\Auth\PasswordResetController;
use App\Http\Controllers\Auth\RegisterController;
use App\Http\Controllers\Auth\TwoFactorController;
use App\Http\Controllers\Cms\CommunityController;
use App\Http\Controllers\Cms\ForumController;
use App\Http\Controllers\Cms\HelpController;
use App\Http\Controllers\Cms\HomeController;
use App\Http\Controllers\Cms\NewsController;
use App\Http\Controllers\Cms\ProfileController;
use App\Http\Controllers\Dcc\DccAutoModerationController;
use App\Http\Controllers\Dcc\DccCatalogueController;
use App\Http\Controllers\Dcc\DccDashboardController;
use App\Http\Controllers\Dcc\DccEconomyController;
use App\Http\Controllers\Dcc\DccForumController;
use App\Http\Controllers\Dcc\DccGamesController;
use App\Http\Controllers\Dcc\DccGardenController;
use App\Http\Controllers\Dcc\DccGroupsController;
use App\Http\Controllers\Dcc\DccModerationController;
use App\Http\Controllers\Dcc\DccRoomsController;
use App\Http\Controllers\Dcc\DccRpController;
use App\Http\Controllers\Dcc\DccStaffController;
use App\Http\Controllers\Dcc\DccSystemController;
use App\Http\Controllers\Dcc\DccUsersController;
use App\Http\Controllers\Dcc\DccWiredController;
use Illuminate\Support\Facades\Route;

// Public CMS
Route::get('/', [HomeController::class, 'index'])->name('home');
Route::get('/hotel', [HomeController::class, 'hotel'])->name('hotel');
Route::get('/news', [NewsController::class, 'index'])->name('news.index');
Route::get('/news/{slug}', [NewsController::class, 'show'])->name('news.show');
Route::get('/help', [HelpController::class, 'index'])->name('help.index');
Route::get('/help/{slug}', [HelpController::class, 'show'])->name('help.show');
Route::get('/staff', [CommunityController::class, 'staff'])->name('staff');
Route::get('/stats', [CommunityController::class, 'stats'])->name('stats');

// Forums. Reading is public where a board allows it; writing needs an account,
// which the routes below enforce rather than the controller guessing.
Route::get('/forum', [ForumController::class, 'index'])->name('forum.index');
Route::get('/forum/board/{slug}', [ForumController::class, 'category'])->name('forum.category');
Route::get('/forum/thread/{id}', [ForumController::class, 'thread'])->name('forum.thread');
Route::get('/forum/group/{group}', [ForumController::class, 'groupForum'])->name('forum.group');

Route::view('/privacy', 'cms.privacy')->name('privacy');
Route::view('/terms', 'cms.terms')->name('terms');

// Auth
Route::middleware('guest')->group(function () {
    Route::get('/register', [RegisterController::class, 'show'])->name('register');
    Route::post('/register', [RegisterController::class, 'store']);
    Route::get('/login', [LoginController::class, 'show'])->name('login');
    Route::post('/login', [LoginController::class, 'store']);
    Route::get('/password/reset', [PasswordResetController::class, 'show'])->name('password.request');
    Route::post('/password/email', [PasswordResetController::class, 'email'])->name('password.email');
    Route::get('/password/reset/{token}', [PasswordResetController::class, 'form'])->name('password.reset');
    Route::post('/password/reset', [PasswordResetController::class, 'update'])->name('password.update');
});

Route::post('/logout', [LoginController::class, 'destroy'])->name('logout')->middleware('auth');

// 2FA
Route::middleware('auth')->prefix('2fa')->group(function () {
    Route::get('/', [TwoFactorController::class, 'show'])->name('2fa.show');
    Route::post('/verify', [TwoFactorController::class, 'verify'])->name('2fa.verify');
    Route::get('/setup', [TwoFactorController::class, 'setup'])->name('2fa.setup');
    Route::post('/enable', [TwoFactorController::class, 'enable'])->name('2fa.enable');
    Route::post('/disable', [TwoFactorController::class, 'disable'])->name('2fa.disable');
});

// Authenticated CMS
Route::middleware(['auth', '2fa'])->group(function () {
    Route::get('/profile', [ProfileController::class, 'show'])->name('profile.show');
    Route::put('/profile', [ProfileController::class, 'update'])->name('profile.update');
    Route::post('/profile/motto', [ProfileController::class, 'updateMotto'])->name('profile.motto');
    Route::get('/profile/{username}', [ProfileController::class, 'view'])->name('profile.view');

    // Forum writing and moderation
    Route::post('/forum/board/{slug}/thread', [ForumController::class, 'storeThread'])->name('forum.thread.store');
    Route::post('/forum/group/{group}/thread', [ForumController::class, 'storeGroupThread'])->name('forum.group.thread.store');
    Route::post('/forum/thread/{id}/reply', [ForumController::class, 'storePost'])->name('forum.post.store');
    Route::put('/forum/post/{id}', [ForumController::class, 'updatePost'])->name('forum.post.update');
    Route::post('/forum/post/{id}/report', [ForumController::class, 'report'])->name('forum.post.report');
    Route::post('/forum/thread/{id}/moderate', [ForumController::class, 'moderateThread'])->name('forum.thread.moderate');
    Route::post('/forum/post/{id}/moderate', [ForumController::class, 'moderatePost'])->name('forum.post.moderate');
});

// DCC (staff only)
Route::middleware(['auth', '2fa', 'dcc'])->prefix('dcc')->name('dcc.')->group(function () {
    Route::get('/', [DccDashboardController::class, 'index'])->name('dashboard');

    // Users (DCC-001 to DCC-007)
    Route::prefix('users')->name('users.')->group(function () {
        Route::get('/', [DccUsersController::class, 'index'])->name('index');
        Route::get('/{id}', [DccUsersController::class, 'show'])->name('show');
        Route::put('/{id}/rank', [DccUsersController::class, 'updateRank'])->name('rank');
        Route::put('/{id}/credits', [DccUsersController::class, 'adjustCredits'])->name('credits');
        Route::put('/{id}/diamonds', [DccUsersController::class, 'adjustDiamonds'])->name('diamonds');
        Route::put('/{id}/nutpoints', [DccUsersController::class, 'adjustNutPoints'])->name('nutpoints');
        Route::post('/{id}/ban', [DccUsersController::class, 'ban'])->name('ban');
        Route::post('/{id}/unban', [DccUsersController::class, 'unban'])->name('unban');
        Route::post('/{id}/mute', [DccUsersController::class, 'mute'])->name('mute');
        Route::post('/{id}/unmute', [DccUsersController::class, 'unmute'])->name('unmute');
        Route::post('/{id}/ticket', [DccUsersController::class, 'issueTicket'])->name('ticket');
        Route::get('/{id}/audit', [DccUsersController::class, 'auditLog'])->name('audit');
        Route::get('/{id}/transactions', [DccUsersController::class, 'transactions'])->name('transactions');
    });

    // Automated moderation
    Route::prefix('automod')->name('automod.')->group(function () {
        Route::get('/', [DccAutoModerationController::class, 'index'])->name('index');
        Route::post('/{id}/review', [DccAutoModerationController::class, 'review'])->name('review');
        Route::get('/rules', [DccAutoModerationController::class, 'rules'])->name('rules');
        Route::post('/rules', [DccAutoModerationController::class, 'storeRule'])->name('rule.store');
        Route::put('/rules/{id}', [DccAutoModerationController::class, 'updateRule'])->name('rule.update');
        Route::post('/rules/{id}/toggle', [DccAutoModerationController::class, 'toggleRule'])->name('rule.toggle');
    });

    // Forums
    Route::prefix('forum')->name('forum.')->group(function () {
        Route::get('/', [DccForumController::class, 'index'])->name('index');
        Route::post('/category', [DccForumController::class, 'storeCategory'])->name('category.store');
        Route::put('/category/{id}', [DccForumController::class, 'updateCategory'])->name('category.update');
        Route::post('/role', [DccForumController::class, 'grantRole'])->name('role.grant');
        Route::delete('/role/{id}', [DccForumController::class, 'revokeRole'])->name('role.revoke');
        Route::get('/reports', [DccForumController::class, 'reports'])->name('reports');
        Route::post('/reports/{id}', [DccForumController::class, 'resolveReport'])->name('report.resolve');
        Route::post('/post/{id}/restore', [DccForumController::class, 'restorePost'])->name('post.restore');
    });

    // Economy (DCC-008 to DCC-013)
    Route::prefix('economy')->name('economy.')->group(function () {
        Route::get('/', [DccEconomyController::class, 'index'])->name('index');
        Route::get('/transactions', [DccEconomyController::class, 'transactions'])->name('transactions');
        Route::post('/grant', [DccEconomyController::class, 'grant'])->name('grant');
        Route::post('/debit', [DccEconomyController::class, 'debit'])->name('debit');
        Route::get('/stats', [DccEconomyController::class, 'stats'])->name('stats');
    });

    // Catalogue (DCC-014 to DCC-019)
    Route::prefix('catalogue')->name('catalogue.')->group(function () {
        Route::get('/', [DccCatalogueController::class, 'index'])->name('index');
        Route::get('/pages', [DccCatalogueController::class, 'pages'])->name('pages');
        Route::post('/pages', [DccCatalogueController::class, 'createPage'])->name('pages.create');
        Route::put('/pages/{id}', [DccCatalogueController::class, 'updatePage'])->name('pages.update');
        Route::delete('/pages/{id}', [DccCatalogueController::class, 'deletePage'])->name('pages.delete');
        Route::get('/items', [DccCatalogueController::class, 'items'])->name('items');
        Route::post('/items', [DccCatalogueController::class, 'createItem'])->name('items.create');
        Route::put('/items/{id}', [DccCatalogueController::class, 'updateItem'])->name('items.update');
        Route::delete('/items/{id}', [DccCatalogueController::class, 'deleteItem'])->name('items.delete');
        Route::get('/limited', [DccCatalogueController::class, 'limited'])->name('limited');
    });

    // Rooms (DCC-020 to DCC-023)
    Route::prefix('rooms')->name('rooms.')->group(function () {
        Route::get('/', [DccRoomsController::class, 'index'])->name('index');
        Route::get('/{id}', [DccRoomsController::class, 'show'])->name('show');
        Route::delete('/{id}', [DccRoomsController::class, 'destroy'])->name('destroy');
        Route::post('/{id}/feature', [DccRoomsController::class, 'toggleFeatured'])->name('feature');
    });

    // Groups (DCC-024 to DCC-026)
    Route::prefix('groups')->name('groups.')->group(function () {
        Route::get('/', [DccGroupsController::class, 'index'])->name('index');
        Route::get('/{id}', [DccGroupsController::class, 'show'])->name('show');
        Route::delete('/{id}', [DccGroupsController::class, 'destroy'])->name('destroy');
        Route::put('/{id}/verify', [DccGroupsController::class, 'verify'])->name('verify');
    });

    // Moderation (DCC-027 to DCC-033)
    Route::prefix('moderation')->name('moderation.')->group(function () {
        Route::get('/', [DccModerationController::class, 'index'])->name('index');
        Route::get('/reports', [DccModerationController::class, 'reports'])->name('reports');
        Route::get('/reports/{id}', [DccModerationController::class, 'report'])->name('report');
        Route::post('/reports/{id}/resolve', [DccModerationController::class, 'resolve'])->name('resolve');
        Route::get('/bans', [DccModerationController::class, 'bans'])->name('bans');
        Route::post('/bans/{id}/lift', [DccModerationController::class, 'liftBan'])->name('bans.lift');
        Route::get('/appeals', [DccModerationController::class, 'appeals'])->name('appeals');
        Route::post('/appeals/{id}/accept', [DccModerationController::class, 'acceptAppeal'])->name('appeals.accept');
        Route::post('/appeals/{id}/deny', [DccModerationController::class, 'denyAppeal'])->name('appeals.deny');
        Route::get('/wordfilter', [DccModerationController::class, 'wordFilter'])->name('wordfilter');
        Route::post('/wordfilter', [DccModerationController::class, 'addWord'])->name('wordfilter.add');
        Route::delete('/wordfilter/{id}', [DccModerationController::class, 'removeWord'])->name('wordfilter.remove');
        Route::get('/chatlogs', [DccModerationController::class, 'chatLogs'])->name('chatlogs');
    });

    // Wired (DCC-034 to DCC-036)
    Route::prefix('wired')->name('wired.')->group(function () {
        Route::get('/', [DccWiredController::class, 'index'])->name('index');
        Route::get('/vars', [DccWiredController::class, 'globalVars'])->name('vars');
        Route::put('/vars/{key}', [DccWiredController::class, 'updateVar'])->name('vars.update');
        Route::delete('/vars/{key}', [DccWiredController::class, 'deleteVar'])->name('vars.delete');
        Route::get('/execlog', [DccWiredController::class, 'execLog'])->name('execlog');
    });

    // Games (DCC-037 to DCC-038)
    Route::prefix('games')->name('games.')->group(function () {
        Route::get('/', [DccGamesController::class, 'index'])->name('index');
        Route::get('/matches', [DccGamesController::class, 'matches'])->name('matches');
        Route::get('/leaderboards', [DccGamesController::class, 'leaderboards'])->name('leaderboards');
        Route::get('/tournaments', [DccGamesController::class, 'tournaments'])->name('tournaments');
    });

    // Garden (DCC-039 to DCC-040)
    Route::prefix('garden')->name('garden.')->group(function () {
        Route::get('/', [DccGardenController::class, 'index'])->name('index');
        Route::post('/season', [DccGardenController::class, 'startSeason'])->name('season');
        Route::get('/plots', [DccGardenController::class, 'plots'])->name('plots');
        Route::get('/goals', [DccGardenController::class, 'goals'])->name('goals');
    });

    // RP (DCC-041 to DCC-043)
    Route::prefix('rp')->name('rp.')->group(function () {
        Route::get('/', [DccRpController::class, 'index'])->name('index');
        Route::get('/characters', [DccRpController::class, 'characters'])->name('characters');
        Route::get('/characters/{id}', [DccRpController::class, 'character'])->name('character');
        Route::post('/characters/{id}/pardon', [DccRpController::class, 'pardon'])->name('pardon');
        Route::get('/factions', [DccRpController::class, 'factions'])->name('factions');
        Route::get('/court', [DccRpController::class, 'court'])->name('court');
        Route::get('/bank', [DccRpController::class, 'bank'])->name('bank');
        Route::get('/laws', [DccRpController::class, 'laws'])->name('laws');
        Route::post('/laws', [DccRpController::class, 'createLaw'])->name('laws.create');
        Route::delete('/laws/{id}', [DccRpController::class, 'repealLaw'])->name('laws.repeal');
    });

    // Staff (DCC-044)
    Route::prefix('staff')->name('staff.')->group(function () {
        Route::get('/', [DccStaffController::class, 'index'])->name('index');
        Route::get('/online', [DccStaffController::class, 'online'])->name('online');
        Route::get('/actions', [DccStaffController::class, 'actions'])->name('actions');
        Route::get('/commands', [DccStaffController::class, 'commands'])->name('commands');
    });

    // System (DCC-045)
    Route::prefix('system')->name('system.')->group(function () {
        Route::get('/', [DccSystemController::class, 'index'])->name('index');
        Route::get('/settings', [DccSystemController::class, 'settings'])->name('settings');
        Route::put('/settings', [DccSystemController::class, 'updateSettings'])->name('settings.update');
        Route::get('/flags', [DccSystemController::class, 'flags'])->name('flags');
        Route::put('/flags/{key}', [DccSystemController::class, 'toggleFlag'])->name('flags.toggle');
        Route::get('/monitoring', [DccSystemController::class, 'monitoring'])->name('monitoring');
        Route::get('/audit', [DccSystemController::class, 'auditLog'])->name('audit');
        Route::get('/maintenance', [DccSystemController::class, 'maintenance'])->name('maintenance');
        Route::post('/maintenance/enable', [DccSystemController::class, 'enableMaintenance'])->name('maintenance.enable');
        Route::post('/maintenance/disable', [DccSystemController::class, 'disableMaintenance'])->name('maintenance.disable');
    });
});
