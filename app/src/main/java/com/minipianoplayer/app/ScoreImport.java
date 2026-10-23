package com.minipianoplayer.app;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class ScoreImport {
    private ScoreImport() {}
    public static Score read(InputStream input) throws Exception {
        BufferedInputStream stream = input instanceof BufferedInputStream ? (BufferedInputStream) input : new BufferedInputStream(input);
        stream.mark(4);
        byte[] magic = new byte[4];
        int count = stream.read(magic);
        stream.reset();
        if (count >= 4 && magic[0] == 'M' && magic[1] == 'T' && magic[2] == 'h' && magic[3] == 'd') return Midi.read(stream);
        if (count >= 4 && magic[0] == 'R' && magic[1] == 'I' && magic[2] == 'F' && magic[3] == 'F') {
            throw new IOException("不支援 RIFF MIDI，請使用 Standard MIDI File（.mid／.midi）");
        }
        return MusicXml.read(stream);
    }
}
