/*
 * Scan progress comes from /admin/sken/stav, not /api/v1/**. The latter chain is
 * stateless and accepts only a Bearer token from the TV client; a browser session would
 * receive 401. The data and DTO are identical; only authentication differs.
 */
(function ($) {
    'use strict';

    var POLL_MS = 2000;
    var THEME_KEY = 'hc-theme';

    /*
     * Theme switching. The theme itself is already applied by the inline script in
     * fragments/layout.html—waiting for this file would mean a flash of the light theme
     * on every page load. What is left here is the menu: reading the stored choice,
     * writing a new one, and keeping the button icon in sync.
     *
     * Three states, because "auto" is not the same as either fixed theme: it follows the
     * operating system and has to keep doing so after the system setting changes.
     */
    $(function () {
        var button = document.getElementById('hc-theme-button');
        if (button === null) {
            return;
        }

        var icon = document.getElementById('hc-theme-icon');
        var systemDark = window.matchMedia('(prefers-color-scheme: dark)');

        function stored() {
            try {
                var value = window.localStorage.getItem(THEME_KEY);
                return value === 'light' || value === 'dark' ? value : 'auto';
            } catch (e) {
                // Private mode blocks localStorage; the system setting still works.
                return 'auto';
            }
        }

        function apply(choice) {
            var dark = choice === 'dark' || (choice === 'auto' && systemDark.matches);
            document.documentElement.setAttribute('data-bs-theme', dark ? 'dark' : 'light');
            icon.className = 'hc-i ' + (dark ? 'hc-i-moon' : 'hc-i-sun');
            $('.hc-theme-option').each(function () {
                this.setAttribute('aria-checked', String(this.dataset.hcTheme === choice));
            });
        }

        $('.hc-theme-option').on('click', function () {
            var choice = this.dataset.hcTheme;
            try {
                if (choice === 'auto') {
                    window.localStorage.removeItem(THEME_KEY);
                } else {
                    window.localStorage.setItem(THEME_KEY, choice);
                }
            } catch (e) {
                // The choice then lasts only until the page is left.
            }
            apply(choice);
        });

        // Only meaningful while "auto" is selected, hence the re-read instead of a flag.
        systemDark.addEventListener('change', function () {
            apply(stored());
        });

        apply(stored());
    });

    /*
     * Confirmation before an irreversible action. The text is in data-hc-confirm, not
     * onsubmit; Thymeleaf 3.1 disallows variables in on* attributes and recommends data-*.
     */
    $(function () {
        $(document).on('submit', 'form[data-hc-confirm]', function () {
            return window.confirm($(this).data('hc-confirm'));
        });
    });

    /*
     * Library media preview. Video.js gives native <video> consistent controls, fullscreen,
     * and Picture-in-Picture, but the browser still performs decoding. Reject unsupported
     * formats before assigning src so Samba does not start downloading a file that cannot play.
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
            // Without load(), the browser keeps its server connection open.
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

        // The MIME type identifies only the container. An unsupported codec appears only here.
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
                        // A completed scan changed tile counts, so reload the page.
                        window.location.reload();
                    }
                })
                .fail(function () {
                    window.clearInterval(timer);
                });
        }, POLL_MS);
    });
})(jQuery);
