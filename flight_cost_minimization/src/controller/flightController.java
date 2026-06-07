/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package controller;

/**
 *
 * @author Farras Hilmy Zaidan
 */

import algorithm.routePlanning;
import model.HasilRute;
import model.MysqlConnection;
import model.Penerbangan;
import view.mainPenerbangan;

import java.util.List;

public class flightController {
    private MysqlConnection repositori;
    private routePlanning strategi;
    private mainPenerbangan tampilan;

    public flightController(mainPenerbangan tampilan) {
        this.repositori = new MysqlConnection();
        this.tampilan = tampilan;
    }

    public void setStrategi(routePlanning strategi) {
        this.strategi = strategi;
    }

    public void getRute(String asal, String tujuan, String tanggal, int maksTransit) {
        tampilan.cetakPesan("\n[SISTEM] Mengambil data penerbangan dari database...");
        List<Penerbangan> listPenerbangan = repositori.getPenerbanganByTanggal(tanggal);

        if (listPenerbangan.isEmpty()) {
            tampilan.cetakPesan("[SISTEM] Tidak ada data penerbangan.");
            return;
        }

        if (strategi == null) {
            tampilan.cetakPesan("[SISTEM] Algoritma belum dipilih.");
            return;
        }

        tampilan.cetakPesan("[SISTEM] Memproses rute...");

        Runtime runtime = Runtime.getRuntime();
        runtime.gc();

        long memoriSebelum = runtime.totalMemory() - runtime.freeMemory();
        long waktuMulai = System.nanoTime();

        HasilRute hasil = strategi.getRuteTermurah(asal, tujuan, maksTransit, listPenerbangan);

        long waktuSelesai = System.nanoTime();
        long memoriSesudah = runtime.totalMemory() - runtime.freeMemory();

        double waktuEksekusi = (waktuSelesai - waktuMulai) / 1_000_000.0;
        long memoriPakai = Math.max(0, memoriSesudah - memoriSebelum);

        tampilan.tampilkanHasil(hasil, waktuEksekusi, memoriPakai);
    }
}
