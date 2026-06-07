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
import java.util.List;

public interface routePlanning {
    HasilRute getRuteTermurah(String asal, String tujuan, int maksTransit, List<Penerbangan> listPenerbangan);
}
