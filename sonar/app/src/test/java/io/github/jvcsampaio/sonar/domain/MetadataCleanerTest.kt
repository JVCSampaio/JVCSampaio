package io.github.jvcsampaio.sonar.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataCleanerTest {
    private fun check(title: String, channel: String, expectedTitle: String, expectedArtist: String) {
        assertEquals(SongInfo(expectedTitle, expectedArtist), MetadataCleaner.clean(title, channel))
    }

    @Test fun artistDashTitleWithOfficialVideo() =
        check("Coldplay - Yellow (Official Video)", "Coldplay", "Yellow", "Coldplay")

    @Test fun topicChannelKeepsTitle() =
        check("Yellow", "Coldplay - Topic", "Yellow", "Coldplay")

    @Test fun portugueseClipeOficial() =
        check("Henrique e Juliano - ARRANHÃO (Clipe Oficial)", "Henrique e Juliano", "ARRANHÃO", "Henrique e Juliano")

    @Test fun titleDashArtistIsSwappedAndKeepsLive() =
        check("Evidências - Chitãozinho & Xororó (Ao Vivo)", "Chitãozinho & Xororó", "Evidências (Ao Vivo)", "Chitãozinho & Xororó")

    @Test fun vevoChannelIsSplitIntoWords() =
        check("Blinding Lights", "TheWeekndVEVO", "Blinding Lights", "The Weeknd")

    @Test fun officialAudioRemoved() =
        check("The Weeknd - Blinding Lights (Official Audio)", "TheWeekndVEVO", "Blinding Lights", "The Weeknd")

    @Test fun officialLiveVideoKeepsLive() =
        check("Daft Punk - Get Lucky (Official Live Video)", "Daft Punk", "Get Lucky (Live)", "Daft Punk")

    @Test fun pipeSuffixAndOfficialChannel() =
        check("Song | Official Music Video", "Band Official", "Song", "Band")

    @Test fun hashtagsRemoved() =
        check("Anitta - Envolver (Official Music Video) #shorts #anitta", "Anitta", "Envolver", "Anitta")

    @Test fun featKept() =
        check("Calvin Harris - Summer (feat. X) [Official Video]", "CalvinHarrisVEVO", "Summer (feat. X)", "Calvin Harris")

    @Test fun trailingNoiseWithoutBrackets() =
        check("Evidências Clipe Oficial", "Chitãozinho & Xororó", "Evidências", "Chitãozinho & Xororó")

    @Test fun quotedTitle() =
        check("Coldplay \"Yellow\"", "SomeUploader", "Yellow", "Coldplay")

    @Test fun lyricsInPortuguese() =
        check("Marília Mendonça - Supera (Letra)", "Marília Mendonça", "Supera", "Marília Mendonça")

    @Test fun mixedGroupKeepsMeaningfulWords() =
        check("Ana Castela - Pipoco (Ao Vivo e Clipe Oficial)", "Ana Castela", "Pipoco (Ao Vivo)", "Ana Castela")

    @Test fun songCalledVideoIsNotEmptied() =
        check("India.Arie - Video", "IndiaArieVEVO", "Video", "India.Arie")

    @Test fun remasterKept() =
        check("Queen – Bohemian Rhapsody (Remastered 2011)", "Queen Official", "Bohemian Rhapsody (Remastered 2011)", "Queen")

    @Test fun plainTitleUsesChannel() =
        check("Garota de Ipanema", "Tom Jobim", "Garota de Ipanema", "Tom Jobim")
}
