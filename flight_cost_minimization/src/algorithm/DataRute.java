/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package algorithm;

/**
 *
 * @author Farras Hilmy Zaidan
 */

import model.HasilRute;
import model.Penerbangan;

import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public final class DataRute {
    public static final int MIN_TRANSIT_MENIT = 40;

    private DataRute() {
    }

    public static Map<String, List<Penerbangan>> getAdjacencyList(List<Penerbangan> listPenerbangan) {
        Map<String, List<Penerbangan>> daftarRute = new HashMap<>();

        if (listPenerbangan == null) {
            return daftarRute;
        }

        for (Penerbangan penerbangan : listPenerbangan) {
            if (penerbangan == null || penerbangan.asal == null || penerbangan.tujuan == null) {
                continue;
            }

            String asalKey = penerbangan.asal.toUpperCase();
            daftarRute.computeIfAbsent(asalKey, key -> new ArrayList<>()).add(penerbangan);
        }

        for (List<Penerbangan> daftarPenerbangan : daftarRute.values()) {
            daftarPenerbangan.sort(
                Comparator.comparingInt((Penerbangan p) -> p.harga)
                    .thenComparing(p -> p.waktuBerangkat)
                    .thenComparing(p -> p.waktuTiba)
            );
        }

        return daftarRute;
    }

    public static Set<String> getBandaraRelevan(String tujuan, Map<String, List<Penerbangan>> daftarRute) {
        Set<String> bandaraRelevan = new HashSet<>();

        if (tujuan == null || daftarRute == null) {
            return bandaraRelevan;
        }

        Map<String, List<String>> ruteBalik = new HashMap<>();

        for (Map.Entry<String, List<Penerbangan>> dataRute : daftarRute.entrySet()) {
            String asal = dataRute.getKey().toUpperCase();
            for (Penerbangan penerbangan : dataRute.getValue()) {
                String tujuanPenerbangan = penerbangan.tujuan.toUpperCase();
                ruteBalik.computeIfAbsent(tujuanPenerbangan, key -> new ArrayList<>()).add(asal);
            }
        }

        String tujuanKey = tujuan.toUpperCase();
        Queue<String> antrianBandara = new ArrayDeque<>();

        bandaraRelevan.add(tujuanKey);
        antrianBandara.add(tujuanKey);

        while (!antrianBandara.isEmpty()) {
            String bandaraSekarang = antrianBandara.poll();
            for (String bandaraSebelumnya : ruteBalik.getOrDefault(bandaraSekarang, Collections.emptyList())) {
                if (bandaraRelevan.add(bandaraSebelumnya)) {
                    antrianBandara.add(bandaraSebelumnya);
                }
            }
        }

        return bandaraRelevan;
    }

    public static boolean isTransitValid(Penerbangan penerbanganSebelumnya, Penerbangan penerbanganBerikutnya) {
        if (penerbanganSebelumnya == null) {
            return true;
        }

        long selisihMenit = ChronoUnit.MINUTES.between(
            penerbanganSebelumnya.waktuTiba,
            penerbanganBerikutnya.waktuBerangkat
        );

        return selisihMenit >= MIN_TRANSIT_MENIT;
    }

    public static boolean isPernahDikunjungi(String asal, List<Penerbangan> jalur, String bandara) {
        if (bandara == null) {
            return false;
        }

        String bandaraKey = bandara.toUpperCase();

        if (asal != null && asal.equalsIgnoreCase(bandaraKey)) {
            return true;
        }

        if (jalur == null) {
            return false;
        }

        for (Penerbangan penerbangan : jalur) {
            if (penerbangan.tujuan != null && penerbangan.tujuan.equalsIgnoreCase(bandaraKey)) {
                return true;
            }
        }

        return false;
    }

    public static Penerbangan getPenerbanganLangsung(
        String asal,
        String tujuan,
        Map<String, List<Penerbangan>> daftarRute
    ) {
        if (asal == null || tujuan == null || daftarRute == null) {
            return null;
        }

        Penerbangan penerbanganTermurah = null;
        String asalKey = asal.toUpperCase();
        String tujuanKey = tujuan.toUpperCase();

        for (Penerbangan penerbangan : daftarRute.getOrDefault(asalKey, Collections.emptyList())) {
            if (penerbangan.tujuan != null && penerbangan.tujuan.equalsIgnoreCase(tujuanKey)) {
                if (penerbanganTermurah == null || penerbangan.harga < penerbanganTermurah.harga) {
                    penerbanganTermurah = penerbangan;
                }
            }
        }

        return penerbanganTermurah;
    }

    public static HasilRute getHasilRute(String asal, List<Penerbangan> jalur, int totalBiaya) {
        if (jalur == null) {
            return null;
        }

        List<String> daftarBandara = new ArrayList<>();
        daftarBandara.add(asal.toUpperCase());

        for (Penerbangan penerbangan : jalur) {
            daftarBandara.add(penerbangan.tujuan.toUpperCase());
        }

        return new HasilRute(daftarBandara, new ArrayList<>(jalur), totalBiaya);
    }
}
