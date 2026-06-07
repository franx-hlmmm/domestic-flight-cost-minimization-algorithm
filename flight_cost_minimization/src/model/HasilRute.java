/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package model;
import java.util.List;

/**
 *
 * @author Farras Hilmy Zaidan
 */

public class HasilRute {
    public List<String> daftarBandara;
    public List<Penerbangan> detailPenerbangan;
    public int totalBiaya;

    public HasilRute(List<String> daftarBandara, List<Penerbangan> detailPenerbangan, int totalBiaya) {
        this.daftarBandara = daftarBandara;
        this.detailPenerbangan = detailPenerbangan;
        this.totalBiaya = totalBiaya;
    }
}
