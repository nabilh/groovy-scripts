#!/home/nabil/bin/groovy

// NH 02/25/2018


import static groovy.io.FileType.FILES
import static java.util.Calendar.AM_PM
import static java.util.Calendar.DATE
import static java.util.Calendar.HOUR
import static java.util.Calendar.MINUTE
import static java.util.Calendar.MONTH
import static java.util.Calendar.YEAR

final LINE_LIMIT = 1
final BLOB_MAX_LINE_LENGTH = 40
final GV_MAX_LABEL_LENGTH = 10

def (REPO_DIR_NAME, GV_FILE) = parseArgs(args)

println "\nGit Report for $REPO_DIR_NAME"
println "Graphiz GV file ${GV_FILE}\n"

def objects = loadObjects(REPO_DIR_NAME)

def report = getReport(objects, LINE_LIMIT, BLOB_MAX_LINE_LENGTH)
def gvScript = getGvScript(objects, GV_FILE, GV_MAX_LABEL_LENGTH)

println report
writeGvFile(gvScript, GV_FILE)

//GitReport

def getReport(objects, lineLimit, maxLineLength) {

    def lines = []

    objects.each {tuple ->

        def objectName = tuple[0]
        def objectType = tuple[1]
        def content = tuple[2]
        def size = tuple[3]

        def line = sprintf("%s %s", objectName, objectType)
        lines.add(line)

        def noOfLines = numberOfLines(content)
        def length = content.length()

        if (objectType.equals('blob')) {
            content = checkContent(content, lineLimit, maxLineLength)
        }
        content = addLineNumbers(content)
        if (noOfLines == 1) {
            line = sprintf("content %d line, %d chars, %s compressed:\n%s\n", noOfLines, length, size, content)
        } else {
            line = sprintf("content %d lines, %d chars, %s compressed:\n%s\n", noOfLines, length, size, content)
        }
        lines.add(line)
    }
    return lines.join("\n")
}

//Utils {

def getRepoName(repoDirName) {
    def parts = repoDirName.split("/")
    def size = repoDirName.split("/").size()
    return parts[size - 1]
}

def printerr(msg) {
    System.err.print(msg)
}

static printlnerr(msg) {
    System.err.println(msg)
}

def getDateTime() {

    final cal = Calendar.instance

    final year = cal.get(YEAR)
    final month = cal.get(MONTH) + 1
    final day = cal.get(DATE)

    final hour = cal.get(HOUR)
    final min = cal.get(MINUTE)
    final ampm = (cal.get(AM_PM)) ? "pm" : "am"

    final today = sprintf ("%02d/%02d/%d", month, day, year)
    final now = sprintf ("%02d:%02d %s", hour, min, ampm)

    [today, now]
}

def currentDir() {
    //  returns with a period at then end of the name - very strange
    def f = new File("./")
    if (!f.exists()) {
        printlnerr "${f.name} does not exist"
    }
    def path = new File("./").absolutePath
    def n = path.size()
    path = path.substring(0, n - 1)
    path
}

def callOS(command) {

    command = command ?: ""

    def result = ""
    try {
        def process = command.execute()
        result = process.text
    } catch (e) {
        def msg = "Error: call to OS failed. command = $command"
        printlnerr msg
    }

    result = result.trim()
    result
}

def fileSize(String objectName, objectsDirName) {
    def dirName = objectName.substring(0, 2)
    def fileName = objectName.substring(2)
    def objectDirName = objectsDirName + "/" + dirName
    def pathName = objectDirName + "/" + fileName
    def commandLine = "stat -c%s $pathName"
    def result = callOS(commandLine)
    return result.trim()
}

def addLineNumbers(string) {

    if (string.size() == 0) {
        return string
    }
    final result = []
    final lines = string.split("\n")
    def i = 1
    for (line in lines) {
        result += "$i: " + line
        i++
    }
    result.join("\n")
}

def parseArgs(String[] args) {

    def repoDirName

    if (args.size() == 0) {
        repoDirName = currentDir()
    } else if (args.size() == 1) {
        repoDirName = currentDir()
    } else {
        repoDirName = args[0]
    }
    def gvFile = getGvFile(args, repoDirName)
    [repoDirName, gvFile]
}

def getGvFile(args, repoDirName) {
    def repoName = getRepoName(repoDirName)
    def gvFileName
    if (args.size() == 0) {
        gvFileName = repoName
    } else if (args.size() == 1) {
        gvFileName = args[0]
    } else {
        gvFileName = args[1]
    }

    gvFileName += ".gv"

    def gvFile = new File(repoDirName + gvFileName)
    gvFile
}

def checkContent(String content, lineLimit, maxLineLength) {

    if (numberOfLines(content) > lineLimit) {
        content = limitNumberOfLinesOut(content, lineLimit)
    }
    content = checkLengthOfLines(content, maxLineLength)
    content
}

def numberOfLines(String string) {

    if (string.size() == 0) {
        return 0
    }
    string.split("\n").size()
}

def limitNumberOfLinesOut(String string, Integer lineLimit) {

    def stringSplitUp = string.split("\n")

    def result = []

    def i = 0
    for (line in stringSplitUp) {
        result.add(line)
        i++
        if (i == lineLimit)
            break
    }

    return result.join("\n")
}

//    final BLOB_MAX_LINE_LENGTH = 10
def checkLengthOfLines(content, maxLineLength) {

    def result = []

    def lines = content.split("\n")

    lines.each {String line ->

        if (line.length() > maxLineLength) {
            line = line.substring(0, maxLineLength - 1)
            line = line.padRight(maxLineLength, "*")
        }
        result.add(line)

    }

    result.join("\n")
}
//GvScript {

def getGvScript(objects, File gvFile, maxLabelLength) {

    def (today, now) = getDateTime()

    final GV_SCRIPT_HEADER = /
digraph gitDag {
  "note" [shape=note, label="Git DAG for \n${gvFile.absolutePath}\n$today at $now"]
  node [shape=box, style=rounded, fixedsize=false, size="5,5"]
  splines="line"
  rankdir="LR"
/
    def gvScript = GV_SCRIPT_HEADER
    def scriptLines = []
    def commits = []

    objects.each {tuple ->

        def name = tuple[0]
        def type = tuple[1]
        def content = tuple[2]
        def gvLines = gvLines(name, type, content, commits, maxLabelLength)
        scriptLines.add(gvLines)

    }
    gvScript = gvScript + scriptLines.join("\n")
    def sameRankSpec = setSameRankForCommits(commits)
    gvScript = gvScript + getBranches()
    gvScript = gvScript + sameRankSpec + "\n}\n"
    return gvScript
}

def writeGvFile(gvScript, gvFile) {

    def (today, now) = getDateTime()
    gvFile.delete()
    println "create gv file ${gvFile.absolutePath}"
    gvFile.append("//\n// graphiz file generated $today at $now\n//")
    gvFile.append(gvScript)

}

def setSameRankForCommits(List commits) {
    def sameRank = "  {rank=same;"
    def i = 0
    commits.each {commitRef ->
        i++
        sameRank = sameRank + /"$commitRef"/
        if (i < commits.size()) {
            sameRank += ", "
        }
    }
    sameRank += "}"
    sameRank
}

def gvLines(String name, String type, String content, commits, maxLabelLength) {
    def shortName = name.substring(0, 7)
    def nodeSpec = /  "$shortName" [label="$shortName\n$type/
    if (type.equals("tree")) {
        nodeSpec = nodeSpec + /"]/
        if (content.size() > 1) {
            def lines = content.split("\n")
            lines.each {line ->
                def words = line.split(" ")
                def ref = words[2].substring(0, 7)
                def shortRef = ref.substring(0, 7)
                def edgeSpec = /"$shortName" ->  "$shortRef"/
                def labelSpec = words[2].substring(41)
                nodeSpec = nodeSpec + "\n     " + edgeSpec + /[label="$labelSpec"]/
            }
        }
    } else if (type.equals('blob')) {
        if (content.size() == 0) {
            content = "\n"
        }
        if (!content.contains("\n")) {
            content += "\n"
        }

        def contentLines = content.split("\n")
        def contentSpec
        if (contentLines.size() > 0) {
            contentSpec = contentLines[0]
        } else {
            contentSpec = content
        }

        contentSpec = contentSpec.trim()
        if (contentSpec.size() > maxLabelLength) {
            contentSpec = contentSpec.substring(0, maxLabelLength - 1)
            contentSpec = contentSpec.padRight(maxLabelLength, '*')
        }
        nodeSpec = nodeSpec + /\n$contentSpec"]/
    } else if (type.equals("commit")) {
        commits.add(shortName)
        def commitLines = content.split("\n")
        def lastLineIndex = commitLines.size() - 1
        nodeSpec += /\n${commitLines[lastLineIndex]}"]/
        commitLines.each {line ->
            def words = line.split(" ")
            if (words[0] in ["tree", "parent"]) {
                def ref = words[1]
                def shortRef = ref.substring(0, 7)
                def s = /"$shortName" ->  "$shortRef"/
                nodeSpec = nodeSpec + "\n     " + s
            }
        }
    }
    nodeSpec = nodeSpec + "\n"
    return nodeSpec
}

def getBranches() {
    def list = []
    def result = callOS("git branch -v")
    if (result.size() == 0) {
        printlnerr "*** no branches found ***"
        return "\n"
    }
    assert (result.size() > 0)
    def lines = result.split("\n")
    lines.each {line ->
        def words = line.split(" +")
        def branchSpec = /${words[1]} ->  "${words[2]}"/
        list.add(branchSpec)
        if (words[0].equals("*")) {
            def head = /HEAD -> "${words[2]}"/
            list.add(head)
        }
    }
    def branches = list.join("\n") + "\n"
    return branches
}

//GitObjects {

def loadObjects(repoDirName) {

    final hexChars = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f']

    def objectList = []

    final objectsDirName = repoDirName + ".git/objects"

    final objectsDir = new File(objectsDirName)

    def objectsDirSorted = objectsDir.listFiles().sort {file ->
        -file.lastModified()
    } as List<File>

    objectsDirSorted.each {dir ->
        if (dir.name[0] in hexChars) {
            dir.eachFile(FILES) {file ->
                def objectName = dir.name + file.name
                // makeNode (objectName, type, size)
                // how do we get the type?
                def type = callOS("git cat-file -t $objectName")
                def content = callOS("git cat-file -p $objectName")
                def size = fileSize(objectName, objectsDirName)
                def tuple = new Tuple(objectName, type, content, size)
                objectList.add(tuple)
            }
        }
    }
    objectList
}
//}