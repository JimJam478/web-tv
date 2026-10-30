// YouTube on a TV, injected into every page; does nothing off youtube.com. Ads: same idea as uBlock's json-prune,
// strip the ad fields from player data before the player reads it. Runs after the page's inline scripts (no
// document-start hook in a plain WebView), so a cold-loaded video can still start an ad: that one gets skipped.
// Playback: H.264 up to 30 fps at 720p, player filling the screen on the watch page, hover previews off.
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

    // TV chips decode only H.264 in hardware: AV1/VP9 and 60 fps fall back to software and drop most frames.
    function tooHeavy(type) {
        return /av01|vp0?9|vp8/i.test(type) || +((/framerate=([0-9.]+)/.exec(type) || [])[1] || 0) > 30;
    }
    var supported = MediaSource.isTypeSupported.bind(MediaSource);
    MediaSource.isTypeSupported = function (type) { return !tooHeavy(type) && supported(type); };
    var decodingInfo = navigator.mediaCapabilities.decodingInfo.bind(navigator.mediaCapabilities);
    navigator.mediaCapabilities.decodingInfo = function (c) {
        return c && c.video && (tooHeavy(c.video.contentType) || c.video.framerate > 30)
            ? Promise.resolve({ supported: false, smooth: false, powerEfficient: false }) : decodingInfo(c);
    };

    // Watch page: the player fills the screen like the YouTube TV app, and the page under it stops drawing.
    var watch = '.webtv-watch ';
    var style = document.createElement('style');
    style.textContent = 'ytd-ad-slot-renderer,ytd-in-feed-ad-layout-renderer,#masthead-ad,#player-ads,'
        + 'ytd-banner-promo-renderer,ytd-miniplayer,#video-preview,' + watch + '#masthead-container,'
        + watch + '#secondary,' + watch + '#below{display:none!important}'
        // The containers too: the player sizes itself from them, and stalls at 0:00 if they stay page-sized.
        + watch + '#player-container,' + watch + '#full-bleed-container,' + watch + 'ytd-player,' + watch
        + '#movie_player{position:fixed!important;inset:0!important;width:100vw!important;height:100vh!important;'
        + 'max-height:none!important;z-index:9999!important;background:#000}'
        + watch + '#movie_player .html5-video-container,' + watch + '#movie_player video{position:absolute!important;'
        + 'inset:0!important;width:100%!important;height:100%!important;object-fit:contain}';
    (document.head || document.documentElement).appendChild(style);

    // ponytail: polls 4x a second on YouTube pages; a MutationObserver on the player's class is the upgrade.
    var mutedByUs = false, qualityFor = null;
    setInterval(function () {
        var isWatch = location.pathname == '/watch', root = document.documentElement;
        if (root.classList.contains('webtv-watch') != isWatch) {
            root.classList.toggle('webtv-watch', isWatch);
            window.dispatchEvent(new Event('resize'));   // the player re-measures and fills its new box
            var left = !isWatch && document.getElementById('movie_player');
            // Back stops the video, as the TV app does: no miniplayer, and a paused player keeps using CPU.
            if (left && left.stopVideo) left.stopVideo();
        }
        // Hover previews: a thumbnail under the pointer plays muted video, costing as much CPU as a real one.
        var preview = document.querySelector('#video-preview video');
        if (preview && !preview.paused) preview.pause();
        var player = document.getElementById('movie_player'), video = player && player.querySelector('video');
        if (!video) return;
        var id = player.getVideoData && player.getVideoData().video_id;
        if (id && id != qualityFor && player.setPlaybackQualityRange) {   // 1080p doubles the CPU: laggy pointer
            player.setPlaybackQualityRange('hd720', 'hd720');
            qualityFor = id;
        }
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
