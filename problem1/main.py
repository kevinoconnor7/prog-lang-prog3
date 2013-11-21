""" Programming Languages Project 3 Problem 1 """

import sys
from os import listdir
from os.path import join, splitext, isfile, isdir
from FileObjects import JavaFile, Dir

def main(root):
    """ Main function call """
    root_dir = browse_directory(root)
    print "RESULTS:"
    root_dir.pretty_print()

def get_immediate_subdirs(root):
    """ Returns paths of all immediate subdirectories of the given path """

    # pylint: disable=W0141
    return filter(isdir, [join(root, f) for f in listdir(root)])

def get_immediate_files_by_ext(root, ext):
    """ Returns paths of all immediate files of the given path with the
     given extension """

    # Filter with isfile() on all files in directory that have the extension ext
    # pylint: disable=W0141
    return filter(isfile, [join(root, f) for f in listdir(root) \
     if splitext(f)[1] == '.'+ext])

def browse_directory(root):
    """ Return Dir object built recursively """

    # list of JavaFile objects for all immediate files with extension "java"
    files = [JavaFile(path) for path in \
     get_immediate_files_by_ext(root, "java")]

    # Recursively call browseDirectory() on all immediate subdirs
    # pylint: disable=W0141
    subdirs = map(browse_directory, get_immediate_subdirs(root))

    return Dir(root, files, subdirs)

if __name__ == '__main__':
    assert len(sys.argv) > 1, "There must be a least one arguement provided"
    assert sys.argv[1] is not None, "No non-falsey argument value provided"
    main(sys.argv[1])
