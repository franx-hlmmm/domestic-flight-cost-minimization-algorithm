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

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class Dijkstra implements routePlanning {
    private static class StatusRute implements Comparable<StatusRute> {
        String bandara;
        int biaya;
        int jumlahTransit;
        Penerbangan penerbanganSebelumnya;
        List<Penerbangan> jalur;

        StatusRute(
            String bandara,
            int biaya,
            int jumlahTransit,
            Penerbangan penerbanganSebelumnya,
            List<Penerbangan> jalur
        ) {
            this.bandara = bandara.toUpperCase();
            this.biaya = biaya;
            this.jumlahTransit = jumlahTransit;
            this.penerbanganSebelumnya = penerbanganSebelumnya;
            this.jalur = new ArrayList<>(jalur);
        }

        @Override
        public int compareTo(StatusRute statusLain) {
            return Integer.compare(this.biaya, statusLain.biaya);
        }
    }

    private static class LabelRute {
        int biaya;
        int jumlahTransit;
        LocalTime waktuTiba;

        LabelRute(int biaya, int jumlahTransit, LocalTime waktuTiba) {
            this.biaya = biaya;
            this.jumlahTransit = jumlahTransit;
            this.waktuTiba = waktuTiba;
        }
    }

    @Override
    public HasilRute getRuteTermurah(
        String asal,
        String tujuan,
        int maksTransit,
        List<Penerbangan> listPenerbangan
    ) {
        if (asal == null || tujuan == null || maksTransit < 0 || listPenerbangan == null || listPenerbangan.isEmpty()) {
            return null;
        }

        String asalKey = asal.toUpperCase();
        String tujuanKey = tujuan.toUpperCase();

        Map<String, List<Penerbangan>> daftarRute = DataRute.getAdjacencyList(listPenerbangan);
        Set<String> bandaraRelevan = DataRute.getBandaraRelevan(tujuanKey, daftarRute);

        PriorityQueue<StatusRute> antrianRute = new PriorityQueue<>();
        antrianRute.add(new StatusRute(asalKey, 0, -1, null, new ArrayList<>()));

        Map<String, List<LabelRute>> labelRute = new HashMap<>();
        addLabel(labelRute, asalKey, 0, -1, null);

        while (!antrianRute.isEmpty()) {
            StatusRute statusSekarang = antrianRute.poll();

            if (statusSekarang.bandara.equalsIgnoreCase(tujuanKey)) {
                return DataRute.getHasilRute(asalKey, statusSekarang.jalur, statusSekarang.biaya);
            }

            if (statusSekarang.jumlahTransit >= maksTransit) {
                continue;
            }

            for (Penerbangan penerbangan : daftarRute.getOrDefault(statusSekarang.bandara, Collections.emptyList())) {
                String tujuanPenerbangan = penerbangan.tujuan.toUpperCase();

                if (!bandaraRelevan.contains(tujuanPenerbangan)) {
                    continue;
                }

                if (!tujuanPenerbangan.equalsIgnoreCase(tujuanKey)
                    && DataRute.isPernahDikunjungi(asalKey, statusSekarang.jalur, tujuanPenerbangan)) {
                    continue;
                }

                if (!DataRute.isTransitValid(statusSekarang.penerbanganSebelumnya, penerbangan)) {
                    continue;
                }

                int biayaBaru = statusSekarang.biaya + penerbangan.harga;
                int transitBaru = statusSekarang.jumlahTransit + 1;
                LocalTime waktuTibaBaru = penerbangan.waktuTiba;

                if (isLabelDidominasi(labelRute, tujuanPenerbangan, biayaBaru, transitBaru, waktuTibaBaru)) {
                    continue;
                }
                addLabel(labelRute, tujuanPenerbangan, biayaBaru, transitBaru, waktuTibaBaru);

                List<Penerbangan> jalurBaru = new ArrayList<>(statusSekarang.jalur);
                jalurBaru.add(penerbangan);

                antrianRute.add(new StatusRute(tujuanPenerbangan, biayaBaru, transitBaru, penerbangan, jalurBaru));
            }
        }

        return null;
    }

    private boolean isLabelDidominasi(
        Map<String, List<LabelRute>> labelRute,
        String bandara,
        int biaya,
        int jumlahTransit,
        LocalTime waktuTiba
    ) {
        for (LabelRute label : labelRute.getOrDefault(bandara, Collections.emptyList())) {
            if (label.biaya <= biaya
                && label.jumlahTransit <= jumlahTransit
                && isWaktuTidakLebihLambat(label.waktuTiba, waktuTiba)) {
                return true;
            }
        }
        return false;
    }

    private void addLabel(
        Map<String, List<LabelRute>> labelRute,
        String bandara,
        int biaya,
        int jumlahTransit,
        LocalTime waktuTiba
    ) {
        List<LabelRute> daftarLabel = labelRute.computeIfAbsent(bandara, key -> new ArrayList<>());

        daftarLabel.removeIf(label -> biaya <= label.biaya
            && jumlahTransit <= label.jumlahTransit
            && isWaktuTidakLebihLambat(waktuTiba, label.waktuTiba));

        daftarLabel.add(new LabelRute(biaya, jumlahTransit, waktuTiba));
    }

    private boolean isWaktuTidakLebihLambat(LocalTime waktuA, LocalTime waktuB) {
        if (waktuA == null) {
            return true;
        }
        if (waktuB == null) {
            return false;
        }
        return !waktuA.isAfter(waktuB);
    }
}
