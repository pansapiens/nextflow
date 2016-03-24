/*
 * Copyright (c) 2013-2016, Centre for Genomic Regulation (CRG).
 * Copyright (c) 2013-2016, Paolo Di Tommaso and the respective authors.
 *
 *   This file is part of 'Nextflow'.
 *
 *   Nextflow is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Nextflow is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Nextflow.  If not, see <http://www.gnu.org/licenses/>.
 */

package nextflow.splitter
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

import nextflow.Channel
import nextflow.Session
import spock.lang.Specification
import test.TestHelper
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class TextSplitterTest extends Specification {

    def testTextByLine() {

        expect:
        new TextSplitter().target("Hello\nworld\n!").list() == ['Hello\n','world\n','!\n']
        new TextSplitter().target("Hello\nworld\n!").options(each:{ it.trim().reverse() }) .list()  == ['olleH','dlrow','!']

    }

    def testTextByLineWithLimit() {

        expect:
        new TextSplitter(limit: 3).target("1\n2\n3\n4\n5").list() == ['1\n','2\n','3\n']

    }

    def testSplitLinesByCount () {

        expect:
        new TextSplitter().target("Hello\nHola\nHalo").list() == ['Hello\n', 'Hola\n', 'Halo\n']
        new TextSplitter().options(by:3).target("11\n22\n33\n44\n55").list() == [ '11\n22\n33\n', '44\n55\n' ]
        new TextSplitter().options(by:2).target("11\n22\n33\n44\n55").list() == [ '11\n22\n', '33\n44\n', '55\n' ]
        new TextSplitter().options(by:2).target('Hello\nworld\n!').list() == ['Hello\nworld\n','!\n']
    }

    def testSplitFileByLine () {

        setup:
        def file = File.createTempFile('chunk','test')
        file.deleteOnExit()
        file.text = '''\
        line1
        line2
        line3
        line4
        line5
        '''.stripIndent()

        when:
        def lines = new TextSplitter().target(file).list()

        then:
        lines[0] == 'line1\n'
        lines[1] == 'line2\n'
        lines[2]== 'line3\n'
        lines[3] == 'line4\n'
        lines[4] == 'line5\n'

        when:
        def channel = new TextSplitter().target(file).options(by:2).channel()
        then:
        channel.val == 'line1\nline2\n'
        channel.val == 'line3\nline4\n'
        channel.val == 'line5\n'
        channel.val == Channel.STOP

    }

    def testSplitChannel() {

        when:
        def channel = new TextSplitter().target("Hello\nworld\n!").channel()
        then:
        channel.val == 'Hello\n'
        channel.val == 'world\n'
        channel.val == '!\n'
        channel.val == Channel.STOP

    }

    def testSplitTextFile() {

        given:
        def folder = TestHelper.createInMemTempDir()
        def file = folder.resolve('file.txt')
        file.text = 'a\nbb\nccc'

        when:
        def channel = new TextSplitter().target(file).channel()
        then:
        channel.val == 'a\n'
        channel.val == 'bb\n'
        channel.val == 'ccc\n'
        channel.val == Channel.STOP

    }

    def testSplitGzipTextFile() {

        given:
        def folder = TestHelper.createInMemTempDir()
        def file = folder.resolve('file.txt.gz')
        def out = new GZIPOutputStream(Files.newOutputStream(file))
        out << 'a\nbb\nccc'
        out.close()

        when:
        def channel = new TextSplitter().target(file).channel()
        then:
        channel.val == 'a\n'
        channel.val == 'bb\n'
        channel.val == 'ccc\n'
        channel.val == Channel.STOP

    }

    def testSplitGzipTextFileWithDecompressOption() {

        given:
        def folder = TestHelper.createInMemTempDir()
        def file = folder.resolve('file.txt')
        def out = new GZIPOutputStream(Files.newOutputStream(file))
        out << 'a\nbb\nccc'
        out.close()

        when:
        def channel = new TextSplitter().target(file).options(decompress: true).channel()
        then:
        channel.val == 'a\n'
        channel.val == 'bb\n'
        channel.val == 'ccc\n'
        channel.val == Channel.STOP

    }

    def testSplitChunkToFiles() {

        given:
        def folder = TestHelper.createInMemTempDir()

        String text = '''\
        line1
        line2
        line3
        line4
        line5
        line6
        line7
        '''.stripIndent()

        when:
        def chunks = new TextSplitter().options(by:3, file: folder).target(text).list()
        then:
        chunks.size() == 3
        chunks[0].text == '''line1\nline2\nline3\n'''
        chunks[1].text == '''line4\nline5\nline6\n'''
        chunks[2].text == '''line7\n'''

        chunks[0] == folder.resolve('chunk.1')
        chunks[1] == folder.resolve('chunk.2')
        chunks[2] == folder.resolve('chunk.3')

    }

    def testSplitFileToFiles() {

        given:
        def folder = TestHelper.createInMemTempDir()
        def source = folder.resolve('my_file.txt')
        source.text = '''\
                    line1
                    line2
                    line3
                    line4
                    line5
                    line6
                    line7
                    '''.stripIndent()

        when:
        def chunks = new TextSplitter().options(by:3, file: folder).target(source).list()
        then:
        chunks.size() == 3
        chunks[0].text == '''line1\nline2\nline3\n'''
        chunks[1].text == '''line4\nline5\nline6\n'''
        chunks[2].text == '''line7\n'''

        chunks[0] == folder.resolve('my_file.1.txt')
        chunks[1] == folder.resolve('my_file.2.txt')
        chunks[2] == folder.resolve('my_file.3.txt')

    }

    def testSplitTextTuples() {

        given:
        def folder = TestHelper.createInMemTempDir()
        def file = folder.resolve('lines.txt')
        file.text = '''\
        line1
        line2
        line3
        line4
        '''.stripIndent()

        when:
        def chunks = new TextSplitter().target([file, file.name]).list()
        then:
        chunks.size() == 4
        chunks[0] == ['line1\n', 'lines.txt']
        chunks[1] == ['line2\n', 'lines.txt']
        chunks[2] == ['line3\n', 'lines.txt']
        chunks[3] == ['line4\n', 'lines.txt']

    }

    def testSplitTextTuplesWithElement() {

        given:
        def folder = TestHelper.createInMemTempDir()
        def file = folder.resolve('lines.txt')
        file.text = '''\
        line1
        line2
        line3
        line4
        '''.stripIndent()

        when:
        def chunks = new TextSplitter().target([1, file.name, file]).options(elem:2).list()
        then:
        chunks.size() == 4
        chunks[0] == [1, 'lines.txt', 'line1\n']
        chunks[1] == [1, 'lines.txt', 'line2\n']
        chunks[2] == [1, 'lines.txt', 'line3\n']
        chunks[3] == [1, 'lines.txt', 'line4\n']

    }

    def testSplitTupleWithFileToFileChunks() {

        given:
        def folder = TestHelper.createInMemTempDir()

        def session = new Session()
        session.workDir = folder.resolve('work')

        def file = folder.resolve('lines.txt')
        file.text = '''\
        line1
        line2
        line3
        line4
        '''.stripIndent()

        when:
        def chunks = new TextSplitter().target([1, file]).options(elem:1,  by:2, file:true).list()
        then:
        chunks.size() == 2
        chunks[0][0] == 1
        chunks[1][0] == 1

        chunks[0][1].name == 'lines.1.txt'
        chunks[1][1].name == 'lines.2.txt'

        chunks[0][1].text == 'line1\nline2\n'
        chunks[1][1].text == 'line3\nline4\n'

    }


}
