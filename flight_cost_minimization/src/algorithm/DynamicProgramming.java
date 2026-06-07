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
import java.util.Set;

public class DynamicProgramming implements routePlanning {
    private static class StatusRute {
        int biaya;
        Penerbangan penerbanganSebelumnya;
        List<Penerbangan> jalur;

        StatusRute(int biaya, Penerbangan penerbanganSebelumnya, List<Penerbangan> jalur) {
            this.biaya = biaya;
            this.penerbanganSebelumnya = penerbanganSebelumnya;
            this.jalur = new ArrayList<>(jalur);
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

        int maksJumlahPenerbangan = maksTransit + 1;

        List<Map<String, List<StatusRute>>> tabelRute = new ArrayList<>();
        for (int i = 0; i <= maksJumlahPenerbangan; i++) {
            tabelRute.add(new HashMap<>());
        }

        tabelRute.get(0)
            .computeIfAbsent(asalKey, key -> new ArrayList<>())
            .add(new StatusRute(0, null, new ArrayList<>()));

        Map<String, List<LabelRute>> labelRute = new HashMap<>();
        addLabel(labelRute, asalKey, 0, -1, null);

        int biayaTerbaik = Integer.MAX_VALUE;
        List<Penerbangan> jalurTerbaik = null;

        Penerbangan penerbanganLangsung = DataRute.getPenerbanganLangsung(asalKey, tujuanKey, daftarRute);
        if (penerbanganLangsung != null) {
            biayaTerbaik = penerbanganLangsung.harga;
            jalurTerbaik = new ArrayList<>();
            jalurTerbaik.add(penerbanganLangsung);
        }

        for (int jumlahPenerbangan = 0; jumlahPenerbangan < maksJumlahPenerbangan; jumlahPenerbangan++) {
            Map<String, List<StatusRute>> tahapSekarang = tabelRute.get(jumlahPenerbangan);

            for (Map.Entry<String, List<StatusRute>> dataTahap : tahapSekarang.entrySet()) {
                String bandaraSekarang = dataTahap.getKey();
                List<StatusRute> daftarStatus = new ArrayList<>(dataTahap.getValue());

                for (StatusRute statusSekarang : daftarStatus) {
                    for (Penerbangan penerbangan : daftarRute.getOrDefault(bandaraSekarang, Collections.emptyList())) {
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
                        if (biayaBaru >= biayaTerbaik) {
                            continue;
                        }

                        int transitBaru = jumlahPenerbangan;
                        LocalTime waktuTibaBaru = penerbangan.waktuTiba;

                        if (isLabelDidominasi(labelRute, tujuanPenerbangan, biayaBaru, transitBaru, waktuTibaBaru)) {
                            continue;
                        }
                        addLabel(labelRute, tujuanPenerbangan, biayaBaru, transitBaru, waktuTibaBaru);

                        List<Penerbangan> jalurBaru = new ArrayList<>(statusSekarang.jalur);
                        jalurBaru.add(penerbangan);

                        StatusRute statusBaru = new StatusRute(biayaBaru, penerbangan, jalurBaru);
                        tabelRute.get(jumlahPenerbangan + 1)
                            .computeIfAbsent(tujuanPenerbangan, key -> new ArrayList<>())
                            .add(statusBaru);

                        if (tujuanPenerbangan.equalsIgnoreCase(tujuanKey) && biayaBaru < biayaTerbaik) {
                            biayaTerbaik = biayaBaru;
                            jalurTerbaik = jalurBaru;
                        }
                    }
                }
            }
        }

        return DataRute.getHasilRute(asalKey, jalurTerbaik, biayaTerbaik);
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
