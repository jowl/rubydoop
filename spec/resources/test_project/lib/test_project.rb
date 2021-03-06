# encoding: utf-8

require 'rubydoop'
require 'json'
require 'openssl' # this just asserts that jruby-openssl was packaged correctly

require 'word_count'
require 'uniques'


Rubydoop.configure do |input_path, output_path|
  parallel do
    job 'word_count plain' do
      input input_path
      output "#{output_path}/word_count-plain"

      mapper WordCount::Mapper
      reducer WordCount::Reducer

      output_key Hadoop::Io::Text
      output_value Hadoop::Io::IntWritable
    end

    job 'word_count custom' do
      input input_path, format: WordCount::InputFormat
      output "#{output_path}/word_count-custom"

      mapper WordCount::Mapper
      combiner WordCount::AliceDoublingCombiner
      reducer WordCount::Reducer

      output_key Hadoop::Io::Text
      output_value Hadoop::Io::IntWritable
    end
  end

  job 'difference' do
    input "#{output_path}/word_count-{plain,custom}", format: :key_value_text
    output "#{output_path}/word_count-diff"

    map_output_key Hadoop::Io::Text
    map_output_value Hadoop::Io::Text

    reducer WordCount::DiffReducer

    output_key Hadoop::Io::Text
    output_value Hadoop::Io::Text
  end
end

cc = Rubydoop::ConfigurationDefinition.new
cc.job 'uniques' do
  input cc.arguments[0]
  output "#{cc.arguments[1]}/uniques"

  mapper Uniques::Mapper
  reducer Uniques::Reducer

  partitioner Uniques::Partitioner
  grouping_comparator Uniques::GroupingComparator

  map_output_value Hadoop::Io::Text
  output_key Hadoop::Io::Text
  output_value Hadoop::Io::IntWritable
end
