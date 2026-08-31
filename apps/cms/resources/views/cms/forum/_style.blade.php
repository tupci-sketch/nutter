<style>
    .board-list { display: flex; flex-direction: column; gap: 0.6rem; }
    .board {
        display: grid; grid-template-columns: 1fr auto auto;
        gap: 1rem; align-items: center;
        padding: 0.85rem 1rem; background: var(--bg-deep);
        border: 1px solid var(--ridge); border-radius: 6px;
    }
    .board:hover { border-color: var(--brass-dim); }
    .board .name { font-weight: 600; font-size: 1rem; }
    .board .desc { font-size: 0.82rem; color: var(--muted); margin-top: 0.15rem; }
    .board .count { text-align: center; min-width: 66px; }
    .board .count .v { font-weight: 700; color: var(--brass); }
    .board .count .k {
        font-family: var(--mono); font-size: 0.62rem; letter-spacing: 0.1em;
        text-transform: uppercase; color: var(--muted);
    }
    .board .latest { font-size: 0.78rem; color: var(--muted); min-width: 130px; text-align: right; }

    .thread-row {
        display: grid; grid-template-columns: 34px 1fr auto auto;
        gap: 0.8rem; align-items: center;
        padding: 0.6rem 0.75rem; background: var(--bg-deep);
        border: 1px solid var(--ridge); border-radius: 6px;
    }
    .thread-row + .thread-row { margin-top: 0.45rem; }
    .thread-row .title { font-weight: 600; }
    .thread-row .sub { display: block; font-size: 0.76rem; color: var(--muted); margin-top: 0.1rem; }
    .thread-row .figure { width: 34px; height: 34px; font-size: 0.9rem; }

    .flag {
        display: inline-block; margin-right: 0.35rem; padding: 0.05rem 0.4rem;
        border-radius: 3px; font-family: var(--mono); font-size: 0.62rem;
        letter-spacing: 0.08em; text-transform: uppercase;
    }
    .flag-pinned { background: rgba(240,160,64,0.16); color: var(--brass); }
    .flag-locked { background: rgba(139,145,180,0.18); color: var(--muted); }
    .flag-hidden { background: rgba(233,69,96,0.16); color: #ff8fa3; }

    .post { display: grid; grid-template-columns: 150px 1fr; gap: 0; }
    .post + .post { border-top: 1px solid var(--ridge); }
    .post .who {
        padding: 1rem; text-align: center; background: var(--bg-deep);
        border-right: 1px solid var(--ridge);
    }
    .post .who .figure { margin: 0 auto 0.5rem; }
    .post .who .name { font-weight: 700; }
    .post .who .sub { font-size: 0.72rem; color: var(--muted); margin-top: 0.2rem; }
    .post .said { padding: 1rem; min-width: 0; }
    .post .body { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.55; }
    .post .stamp {
        display: flex; justify-content: space-between; gap: 1rem;
        font-size: 0.74rem; color: var(--muted); margin-bottom: 0.6rem;
    }
    .post .tools { margin-top: 0.9rem; display: flex; flex-wrap: wrap; gap: 0.4rem; }
    .post.is-hidden { opacity: 0.62; }
    .post .hidden-note {
        margin-bottom: 0.6rem; padding: 0.45rem 0.6rem; border-radius: 4px;
        background: rgba(233,69,96,0.12); border: 1px solid var(--rose);
        color: #ff8fa3; font-size: 0.78rem;
    }

    .composer textarea { min-height: 130px; resize: vertical; }
    .inline-form { display: inline; }

    @media (max-width: 700px) {
        .board { grid-template-columns: 1fr; }
        .board .count, .board .latest { text-align: left; }
        .thread-row { grid-template-columns: 1fr; }
        .thread-row .figure { display: none; }
        .post { grid-template-columns: 1fr; }
        .post .who {
            display: flex; align-items: center; gap: 0.7rem;
            text-align: left; border-right: none; border-bottom: 1px solid var(--ridge);
        }
        .post .who .figure { margin: 0; }
    }
</style>
