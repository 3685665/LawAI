package com.lawai.api.service;

import com.lawai.api.dto.PetitionTrainingChecklistItemDto;
import com.lawai.api.dto.PetitionTrainingModuleDto;
import com.lawai.api.dto.PetitionTrainingPromptDto;
import com.lawai.api.dto.PetitionTrainingResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TrainingService {

  public PetitionTrainingResponse petitionDrafting() {
    return new PetitionTrainingResponse(
        "Dilekce Atolyesi",
        "Avukat icin stajyer modu",
        "Dilekce yazarken dosyayi sadece olu bir metin olarak degil, egitilebilir bir dosya olarak ele alan yardimci panel.",
        List.of(
            "Dosyayi hizli ogrenmek isteyen avukatlar",
            "Stajyerine is akisini gostermek isteyen hukuk ekipleri",
            "Dilekceyi yazmadan once hukuki omurgayi test etmek isteyen kullanicilar"
        ),
        List.of(
            new PetitionTrainingModuleDto(
                "dosya-okuma",
                "1. Dosyayi okuma",
                "Vakayi, taraflari, tarihleri ve usul risklerini bir stajyere anlatir gibi ayristirir.",
                List.of(
                    "Olaya ait kronolojiyi cikar",
                    "Taraf rollerini ve ihtilaf konusunu ayir",
                    "Eksik bilgi, catisan anlatim ve zaman asimi riskini isaretle"
                ),
                List.of(
                    "Dosyadan 5 maddelik olay ozeti uret",
                    "Karsi tarafin olasi itirazlarini listele",
                    "Mahkemeye gitmeden once sorulmasi gereken 3 kritik soruyu yaz"
                )
            ),
            new PetitionTrainingModuleDto(
                "hukuki-iskelet",
                "2. Hukuki iskeleti kurma",
                "Talep sonucunu, hukuki sebebi ve delil mantigini sade bir omurga halinde toplar.",
                List.of(
                    "Talep ile vakalar arasindaki baglantiyi kur",
                    "Her iddianin hangi delille desteklenecegini yaz",
                    "Usuli zorunluluklari ayri bir katmanda takip et"
                ),
                List.of(
                    "Talep sonucunu bir cumlede netlestir",
                    "Her hukuki sebep icin 1 destekleyici karar basligi oner",
                    "Eksik delil varsa hangi belgeyi istemen gerektigini soyle"
                )
            ),
            new PetitionTrainingModuleDto(
                "dilekce-teknigi",
                "3. Dilekce teknigi",
                "Metni mahkeme diliyle ama okunabilir bir yapida kurar.",
                List.of(
                    "Giris, vakalar, hukuki nedenler ve sonuc kisimlarini ayir",
                    "Kisa ve net paragraf mantigi kullan",
                    "Tarih, belge ve talep tutarliligini kontrol et"
                ),
                List.of(
                    "Dilekcenin ilk paragrafini 3 farkli tonda yeniden yaz",
                    "Bir stajyere anlatir gibi gereksiz sozculuklari temizle",
                    "En guclu iki cumleyi daha vurucu hale getir"
                )
            ),
            new PetitionTrainingModuleDto(
                "son-kontrol",
                "4. Son kontrol",
                "Imza, ekler, vekalet, harc ve sure kontrollerini kapatir.",
                List.of(
                    "Ekler listesi ile dosya icerigini karsilastir",
                    "Harc ve gider avansi kontrolu yap",
                    "Tebligat, yetki ve sure kaybini ayri isle"
                ),
                List.of(
                    "Dosyayi sevk edilmeden once kontrol listesi uret",
                    "Eksik belge icin oncelik sirasi ver",
                    "Riskli noktalar icin kirmizi bayraklar belirle"
                )
            )
        ),
        List.of(
            new PetitionTrainingPromptDto(
                "Ozet cikar",
                "Dosyadaki olaylari bir stajyere anlatir gibi 5 maddede ozetle.",
                "Kisa ve kronolojik olay ozeti"
            ),
            new PetitionTrainingPromptDto(
                "Talep netlestir",
                "Bu dosyada mahkemeden tam olarak ne istendigini tek cumlede kur.",
                "Talep sonucuna uygun net cumle"
            ),
            new PetitionTrainingPromptDto(
                "Eksik ara",
                "Bu dilekcede usulen eksik kalabilecek noktalar neler?",
                "Eksik belge ve usul risk listesi"
            ),
            new PetitionTrainingPromptDto(
                "Stajyer sorusu",
                "Bir stajyerin sorabilecegi en kritik 3 soruyu yaz ve cevapla.",
                "Egitici soru-cevap seti"
            )
        ),
        List.of(
            new PetitionTrainingChecklistItemDto("Vakalar", "Kronoloji ve taraflar tutarli mi?", true),
            new PetitionTrainingChecklistItemDto("Talep", "Talep sonucu bir karin cumlesine indirgenebiliyor mu?", true),
            new PetitionTrainingChecklistItemDto("Delil", "Her ana iddia en az bir belge veya delille baglaniyor mu?", true),
            new PetitionTrainingChecklistItemDto("Usul", "Sure, yetki, harc ve zorunlu ekler kontrol edildi mi?", true),
            new PetitionTrainingChecklistItemDto("Dil", "Metin gereksiz tekrar, uzun cumle ve belirsizlikten arindirildi mi?", true)
        ),
        List.of(
            "Vakayi dogrudan sonuca atlama; once omurga kur.",
            "Her iddiayi delilsiz birakma; delil haritasini ayri tut.",
            "Usul risklerini metnin icine gomme; ayri kontrol et.",
            "Kotu yazilmis ama dogru bir dilekce, iyi yazilmis ama eksik bir dilekceden daha zayiftir."
        ),
        List.of("drafting", "review", "risk-check", "stajyer-modu")
    );
  }
}
