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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BranchBound implements routePlanning {
    private int biayaTerbaik;
    private List<Penerbangan> jalurTerbaik;

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

        biayaTerbaik = Integer.MAX_VALUE;
        jalurTerbaik = null;

        Penerbangan penerbanganLangsung = DataRute.getPenerbanganLangsung(asalKey, tujuanKey, daftarRute);
        if (penerbanganLangsung != null) {
            biayaTerbaik = penerbanganLangsung.harga;
            jalurTerbaik = new ArrayList<>();
            jalurTerbaik.add(penerbanganLangsung);
        }

        Map<String, List<LabelRute>> labelRute = new HashMap<>();
        addLabel(labelRute, asalKey, 0, -1, null);

        Set<String> bandaraDikunjungi = new HashSet<>();
        bandaraDikunjungi.add(asalKey);

        prosesRute(
            asalKey,
            tujuanKey,
            maksTransit,
            daftarRute,
            bandaraRelevan,
            labelRute,
            0,
            -1,
            null,
            new ArrayList<>(),
            bandaraDikunjungi
        );

        return DataRute.getHasilRute(asalKey, jalurTerbaik, biayaTerbaik);
    }

    private void prosesRute(
        String bandaraSekarang,
        String tujuanAkhir,
        int maksTransit,
        Map<String, List<Penerbangan>> daftarRute,
        Set<String> bandaraRelevan,
        Map<String, List<LabelRute>> labelRute,
        int biayaSekarang,
        int jumlahTransit,
        Penerbangan penerbanganSebelumnya,
        List<Penerbangan> jalur,
        Set<String> bandaraDikunjungi
    ) {
        if (bandaraSekarang.equalsIgnoreCase(tujuanAkhir)) {
            if (biayaSekarang < biayaTerbaik) {
                biayaTerbaik = biayaSekarang;
                jalurTerbaik = new ArrayList<>(jalur);
            }
            return;
        }

        if (biayaSekarang >= biayaTerbaik) {
            return;
        }

        if (jumlahTransit >= maksTransit) {
            return;
        }

        for (Penerbangan penerbangan : daftarRute.getOrDefault(bandaraSekarang.toUpperCase(), Collections.emptyList())) {
            String tujuanPenerbangan = penerbangan.tujuan.toUpperCase();

            if (!bandaraRelevan.contains(tujuanPenerbangan)) {
                continue;
            }

            if (!tujuanPenerbangan.equalsIgnoreCase(tujuanAkhir) && bandaraDikunjungi.contains(tujuanPenerbangan)) {
                continue;
            }

            if (!DataRute.isTransitValid(penerbanganSebelumnya, penerbangan)) {
                continue;
            }

            int biayaBaru = biayaSekarang + penerbangan.harga;
            if (biayaBaru >= biayaTerbaik) {
                continue;
            }

            int transitBaru = jumlahTransit + 1;
            LocalTime waktuTibaBaru = penerbangan.waktuTiba;

            if (isLabelDidominasi(labelRute, tujuanPenerbangan, biayaBaru, transitBaru, waktuTibaBaru)) {
                continue;
            }
            addLabel(labelRute, tujuanPenerbangan, biayaBaru, transitBaru, waktuTibaBaru);

            jalur.add(penerbangan);
            bandaraDikunjungi.add(tujuanPenerbangan);

            prosesRute(
                tujuanPenerbangan,
                tujuanAkhir,
                maksTransit,
                daftarRute,
                bandaraRelevan,
                labelRute,
                biayaBaru,
                transitBaru,
                penerbangan,
                jalur,
                bandaraDikunjungi
            );

            bandaraDikunjungi.remove(tujuanPenerbangan);
            jalur.remove(jalur.size() - 1);
        }
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
