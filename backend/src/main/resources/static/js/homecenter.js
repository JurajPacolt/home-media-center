/*
 * Priebeh skenu sa dopĺňa z /admin/sken/stav, nie z /api/v1/**. Tamten reťazec je
 * bezstavový a prijíma len Bearer token od TV klienta — prehliadač so session by
 * dostal 401. Dáta aj DTO sú rovnaké, líši sa iba spôsob prihlásenia.
 */
(function ($) {
    'use strict';

    var POLL_MS = 2000;

    /*
     * Potvrdenie pred nezvratnou akciou. Text je v data-hc-confirm, nie v onsubmit —
     * Thymeleaf 3.1 do on* atribútov premenné nepúšťa a sám odporúča data-*.
     */
    $(function () {
        $(document).on('submit', 'form[data-hc-confirm]', function () {
            return window.confirm($(this).data('hc-confirm'));
        });
    });

    /*
     * Náhľad média v knižnici. Video.js dáva natívnemu <video> jednotné ovládanie,
     * fullscreen a Picture-in-Picture, dekódovanie však stále robí prehliadač.
     * Nepodporovaný formát preto odmietneme ešte pred nastavením src, aby sa zo Samby
     * nezačal zbytočne sťahovať súbor, ktorý nemá šancu prehrať.
     */
    $(function () {
        var okno = document.getElementById('hc-preview');
        if (okno === null) {
            return;
        }

        var video = document.getElementById('hc-preview-video');
        var videoKontajner = document.getElementById('hc-preview-video-container');
        var audio = document.getElementById('hc-preview-audio');
        var obrazok = document.getElementById('hc-preview-image');
        var odkaz = document.getElementById('hc-preview-download');
        var hlaska = document.getElementById('hc-preview-unsupported');
        var format = document.getElementById('hc-preview-format');
        var metadataPanel = document.getElementById('hc-preview-metadata');
        var metadataPoster = document.getElementById('hc-preview-poster');
        var metadataFacts = document.getElementById('hc-preview-facts');
        var metadataDescription = document.getElementById('hc-preview-description');
        var videoAktivne = false;
        var videoPodpora = document.createElement('video');

        var videoPrehravac = window.videojs(video, {
            language: 'sk',
            controls: true,
            fluid: true,
            preload: 'metadata',
            playbackRates: [0.5, 0.75, 1, 1.25, 1.5, 2]
        });

        var nepodporovaneVideo = {
            avi: true,
            flv: true,
            mkv: true,
            m2ts: true,
            mpeg: true,
            mpg: true,
            ts: true,
            wmv: true
        };
        var podporovaneObrazky = {
            avif: true,
            bmp: true,
            gif: true,
            jpeg: true,
            jpg: true,
            png: true,
            webp: true
        };

        function daSaOtvorit(kategoria, pripona, contentType) {
            if (kategoria === 'VIDEO') {
                return nepodporovaneVideo[pripona] !== true
                    && videoPodpora.canPlayType(contentType) !== '';
            }
            if (kategoria === 'AUDIO') {
                return audio.canPlayType(contentType) !== '';
            }
            return podporovaneObrazky[pripona] === true;
        }

        function zobrazNepodporovane() {
            videoKontajner.hidden = true;
            audio.hidden = true;
            obrazok.hidden = true;
            hlaska.hidden = false;
        }

        function vycisti() {
            videoAktivne = false;
            videoPrehravac.pause();
            videoPrehravac.reset();
            videoKontajner.hidden = true;

            audio.pause();
            audio.removeAttribute('src');
            // Bez load() drží prehliadač otvorené spojenie na server.
            audio.load();
            audio.hidden = true;

            obrazok.removeAttribute('src');
            obrazok.hidden = true;
            metadataPoster.removeAttribute('src');
            metadataPoster.hidden = true;
            metadataPanel.hidden = true;
            metadataFacts.textContent = '';
            metadataDescription.textContent = '';
            hlaska.hidden = true;
        }

        function zobrazMetadata(tlacidlo) {
            var popis = tlacidlo.attr('data-hc-description') || '';
            var zanre = tlacidlo.attr('data-hc-genres') || '';
            var rok = tlacidlo.attr('data-hc-year') || '';
            var hodnotenie = tlacidlo.attr('data-hc-rating') || '';
            var skupina = tlacidlo.attr('data-hc-group') || '';
            var fakty = [skupina, zanre, rok ? 'Rok ' + rok : '',
                hodnotenie ? '★ ' + hodnotenie + ' / 10' : ''].filter(Boolean);
            var maPoster = tlacidlo.attr('data-hc-has-poster') === 'true';

            if (!popis && fakty.length === 0 && !maPoster) {
                return;
            }
            metadataPanel.hidden = false;
            metadataFacts.textContent = fakty.join(' · ');
            metadataDescription.textContent = popis;
            if (maPoster) {
                metadataPoster.alt = 'Plagát: ' + tlacidlo.data('hc-title');
                metadataPoster.setAttribute('src', tlacidlo.attr('data-hc-poster-url'));
                metadataPoster.hidden = false;
            }
        }

        $(document).on('click', '[data-hc-preview]', function () {
            var tlacidlo = $(this);
            var id = tlacidlo.data('hc-id');
            var kategoria = tlacidlo.data('hc-category');
            var pripona = String(tlacidlo.data('hc-extension') || '').toLowerCase();
            var contentType = tlacidlo.data('hc-type');
            var streamUrl = '/admin/kniznica/' + id + '/stream';

            vycisti();
            document.getElementById('hc-preview-title').textContent = tlacidlo.data('hc-title');
            document.getElementById('hc-preview-subtitle').textContent = tlacidlo.data('hc-subtitle');
            odkaz.setAttribute('href', '/admin/kniznica/' + id + '/stiahnut');
            format.textContent = (pripona ? '.' + pripona.toUpperCase() + ' · ' : '') + contentType;
            zobrazMetadata(tlacidlo);

            if (!daSaOtvorit(kategoria, pripona, contentType)) {
                zobrazNepodporovane();
            } else if (kategoria === 'VIDEO') {
                videoAktivne = true;
                videoKontajner.hidden = false;
                videoPrehravac.src({src: streamUrl, type: contentType});
                videoPrehravac.load();
            } else {
                var prvok = kategoria === 'AUDIO' ? audio : obrazok;
                prvok.hidden = false;
                prvok.setAttribute('src', streamUrl);
            }

            bootstrap.Modal.getOrCreateInstance(okno).show();
        });

        // MIME typ hovorí len o kontajneri. Nepodporovaný kodek sa prejaví až tu.
        videoPrehravac.on('error', function () {
            if (videoAktivne) {
                videoAktivne = false;
                videoPrehravac.reset();
                zobrazNepodporovane();
            }
        });

        [audio, obrazok].forEach(function (prvok) {
            prvok.addEventListener('error', function () {
                if (prvok.getAttribute('src')) {
                    zobrazNepodporovane();
                }
            });
        });

        okno.addEventListener('hidden.bs.modal', vycisti);
    });

    $(function () {
        var panel = $('#hc-scan');
        if (panel.length === 0 || panel.data('running') !== true) {
            return;
        }

        var timer = window.setInterval(function () {
            $.getJSON('/admin/sken/stav')
                .done(function (run) {
                    if (!run) {
                        return;
                    }
                    $('#hc-scan-dirs').text(run.directoriesScanned);
                    $('#hc-scan-files').text(run.filesSeen);
                    $('#hc-scan-delta').text(
                        run.itemsAdded + ' / ' + run.itemsUpdated + ' / ' + run.itemsRemoved);

                    if (run.status !== 'RUNNING') {
                        window.clearInterval(timer);
                        // Dokončený sken zmenil počty v dlaždiciach — načítame stránku odznova.
                        window.location.reload();
                    }
                })
                .fail(function () {
                    window.clearInterval(timer);
                });
        }, POLL_MS);
    });
})(jQuery);
