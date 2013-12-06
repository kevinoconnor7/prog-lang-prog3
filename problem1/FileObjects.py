""" 
    Programming Languages Project 3 Problem 1 
    Kevin O'Connor & Dimitre Dimitrov
"""
""" Contains objects to represent File System objects """

from os.path import splitext, getsize, isdir
from re import sub, findall

class JavaFile(object):
    """ Encapsulates attributes for a .java file """

    def __init__(self, path):
        assert splitext(path)[1] == ".java", \
            "Path does not have extension .java"

        self.path = path
        self.size = getsize(path)
        self.word_count = {
            'public':   0,
            'private':  0,
            'try':      0,
            'catch':    0
        }

        self.gen_word_counts(self.word_count.keys())

    def __repr__(self):
        return "<JavaFile path='%s'>" % (self.path)

    def get_word_count(self, keyword):
        """ Retrieve the count of a keyword in the document """
        self.gen_word_counts([keyword])
        return self.word_count[keyword]

    def gen_word_counts(self, words):
        """ Build up the self.word_count dict with counts of keywords based
         on key name """

        contents = None
        with open(self.path, 'r') as filer:
            contents = filer.read()

        # Strip all block+inline comments from the contents we want to look at
        contents = sub(r'(/\*([^*]|[\r\n]|(\*+([^*/]|[\r\n])))*\*+/)|(//.*)',
            '',
            contents)

        # For every value in words, search for it and save the count
        for value in words:
            self.word_count[value] = len(findall(r'(%s)' % (value), contents))

class Dir(object):
    """ Encapsulates attributes for a Directory """

    def __init__(self, path, files = None, immediateSubDirs = None):
        assert isdir(path), "Path is not a directory"

        if files is None:
            files = []
        if immediateSubDirs is None:
            immediateSubDirs = []

        self.path = path
        self.files = files
        self.immediate_sub_dirs = immediateSubDirs
        self.all_files = files

        # Keep a record of all the files in all subdirs
        for directory in self.immediate_sub_dirs:
            self.all_files += directory.all_files

    def __repr__(self):
        return "<Dir path='%s'>" % (self.path)

    def size(self):
        """ Returns the size in bytes of all subfiles """
        return sum(f.size for f in self.all_files)

    def word_count(self, keyword):
        """ Returns the sum of a given wordcount amongst all subfiles """
        return sum(file.word_count[keyword] for file in self.all_files)

    def pretty_print(self, base=None, depth=0):
        """ Recursively print stats about Dir objects """

        # Truncate the path and left-pad it for the depth of recursion we're at
        path = self.path if base is None else ('-'*depth+self.path[len(base):])

        print "%s\t\t%s bytes\t%s public\t%s private\t%s try\t%s catch" % (
            # Truncate the right-most side of the path if > 30 characters
            ((path[30:] and '...') + path[-30:]).ljust(33),
            ('%d' % self.size()).rjust(6),
            ('%d' % self.word_count('public')).rjust(6),
            ('%d' % self.word_count('private')).rjust(6),
            ('%d' % self.word_count('try')).rjust(6),
            ('%d' % self.word_count('catch')).rjust(6)
        )

        # Recurse down into the subdirectories
        for directory in self.immediate_sub_dirs:
            directory.pretty_print(self.path, depth+1)
