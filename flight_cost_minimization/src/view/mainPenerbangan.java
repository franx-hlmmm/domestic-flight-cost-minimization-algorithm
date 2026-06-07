/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package view;

/**
 *
 * @author Farras Hilmy Zaidan
 */

import algorithm.BranchBound;
import algorithm.Dijkstra;
import algorithm.DynamicProgramming;
import controller.flightController;
import model.HasilRute;
import model.Penerbangan;

import java.util.Scanner;

public class mainPenerbangan {

    public void cetakPesan(String pesan) {
        System.out.println(pesan);
    }

    public void tampilkanHasil(HasilRute hasil, double waktuEksekusi, long memoriPakai) {
        System.out.println("\n================ HASIL PENCARIAN RUTE ================");
        if (hasil == null) {
            System.out.println("Rute tidak ditemukan dengan kriteria tersebut.");
        } else {
            System.out.println("Jalur Bandara : " + hasil.daftarBandara.toString());
            System.out.println("Total Biaya   : Rp " + String.format("%,d", hasil.totalBiaya));
            System.out.printf("Waktu Eksekusi: %.3f ms%n", waktuEksekusi);
            System.out.printf("Memori Pakai  : %.3f MB%n", memoriPakai / (1024.0 * 1024.0));
            System.out.println("Jumlah Transit: " + Math.max(0, hasil.detailPenerbangan.size() - 1));
            System.out.println("\nDetail Penerbangan:");

            for (int i = 0; i < hasil.detailPenerbangan.size(); i++) {
                Penerbangan penerbangan = hasil.detailPenerbangan.get(i);
                System.out.println((i + 1) + ". " + penerbangan.asal + " -> " + penerbangan.tujuan
                    + " | Maskapai: " + penerbangan.maskapai
                    + " | Waktu: " + penerbangan.waktuBerangkat + " - " + penerbangan.waktuTiba
                    + " | Harga: Rp " + String.format("%,d", penerbangan.harga));
            }
        }
        System.out.println("======================================================\n");
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        mainPenerbangan tampilan = new mainPenerbangan();
        flightController controller = new flightController(tampilan);

        System.out.println("Aplikasi Optimasi Rute Penerbangan ASA");
        System.out.print("Masukkan Bandara Asal (Misal: AMQ): ");
        String asal = scanner.nextLine().toUpperCase();

        System.out.print("Masukkan Bandara Tujuan (Misal: CGK): ");
        String tujuan = scanner.nextLine().toUpperCase();

        System.out.print("Tanggal Penerbangan (YYYY-MM-DD): ");
        String tanggal = scanner.nextLine();

        System.out.print("Maksimum Transit yang Diizinkan: ");
        int maksTransit = Integer.parseInt(scanner.nextLine());

        System.out.println("\nPilih Algoritma Pencarian:");
        System.out.println("1. Dijkstra");
        System.out.println("2. Branch and Bound");
        System.out.println("3. Dynamic Programming");
        System.out.print("Pilihan Anda (1/2/3): ");
        int pilihanAlgo = Integer.parseInt(scanner.nextLine());

        switch (pilihanAlgo) {
            case 1:
                controller.setStrategi(new Dijkstra());
                break;
            case 2:
                controller.setStrategi(new BranchBound());
                break;
            case 3:
                controller.setStrategi(new DynamicProgramming());
                break;
            default:
                System.out.println("Pilihan tidak valid.");
                scanner.close();
                return;
        }

        controller.getRute(asal, tujuan, tanggal, maksTransit);
        scanner.close();
    }
}
