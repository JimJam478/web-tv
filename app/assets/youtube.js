// YouTube video ads, injected into every page; does nothing off youtube.com. Same idea as uBlock's json-prune:
// strip the ad fields from player data before the player reads it. Runs after the page's inline scripts (no
// document-start hook in a plain WebView), so a cold-loaded video can still start an ad: that one gets skipped.
(function () {
    if (!/(^|\.)youtube\.com$/.test(location.hostname) || window.webtvYT) return;
    window.webtvYT = 1;

    function prune(o) {
        if (!o || typeof o != 'object') return o;
        delete o.adPlacements;
        delete o.playerAds;
        delete o.adSlots;
        var mr = o.auxiliaryUi && o.auxiliaryUi.messageRenderers;
        if (mr) delete mr.enforcementMessageViewModel;   // the "ad blockers violate YouTube's Terms" popup
        if (o.playerResponse) prune(o.playerResponse);
        return o;
    }
    // Player data arrives through both: fetch().json() for in-app navigation, JSON.parse for some page data.
    var parse = JSON.parse;
    JSON.parse = function () { return prune(parse.apply(this, arguments)); };
    var json = Response.prototype.json;
    Response.prototype.json = function () { return json.apply(this, arguments).then(prune); };
    prune(window.ytInitialPlayerResponse);   // in case the player hasn't read it yet

    var style = document.createElement('style');
    style.textContent = 'ytd-ad-slot-renderer,ytd-in-feed-ad-layout-renderer,#masthead-ad,#player-ads,'
        + 'ytd-banner-promo-renderer{display:none!important}';
    (document.head || document.documentElement).appendChild(style);

    // ponytail: polls 4x a second on YouTube pages; a MutationObserver on the player's class is the upgrade.
    var mutedByUs = false;
    setInterval(function () {
        var player = document.getElementById('movie_player'), video = player && player.querySelector('video');
        if (!video) return;
        if (player.classList.contains('ad-showing')) {
            if (!video.muted) { video.muted = true; mutedByUs = true; }
            if (isFinite(video.duration)) video.currentTime = video.duration;
            var skip = player.querySelector('.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-modern');
            if (skip) skip.click();
        } else if (mutedByUs) {   // same <video> element plays the real video next: give its sound back
            video.muted = false;
            mutedByUs = false;
        }
    }, 250);
})();
